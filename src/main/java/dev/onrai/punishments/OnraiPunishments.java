package dev.onrai.punishments;

import dev.onrai.punishments.command.PunishmentCommands;
import dev.onrai.punishments.gui.HistoryGui;
import dev.onrai.punishments.model.Punishment;
import dev.onrai.punishments.storage.MongoStorage;
import dev.onrai.punishments.storage.SqlStorage;
import dev.onrai.punishments.storage.Storage;
import dev.onrai.punishments.util.MessageFormatter;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class OnraiPunishments extends JavaPlugin implements Listener {
    private Storage storage;
    private ExecutorService io;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final ConcurrentMap<UUID, ChatState> chatCache = new ConcurrentHashMap<>();
    private HistoryGui history;
    private FileConfiguration menu;
    private record ChatState(Punishment mute, long checkedAt) {}
    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.legacyAmpersand();

    @Override public void onEnable() {
        saveDefaultConfig(); saveResource("menu.yml", false);
        menu = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "menu.yml"));
        io = Executors.newFixedThreadPool(4, runnable -> { Thread thread = new Thread(runnable, "OnraiPunishments-IO"); thread.setDaemon(true); return thread; });
        String mode = getConfig().getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        storage = switch (mode) {
            case "sqlite" -> new SqlStorage(this, true);
            case "mysql" -> new SqlStorage(this, false);
            case "mongodb" -> new MongoStorage(getConfig());
            default -> throw new IllegalArgumentException("database.type must be sqlite, mysql or mongodb");
        };
        history = new HistoryGui(this);
        PunishmentCommands command = new PunishmentCommands(this);
        for (String key : List.of("ban","tempban","mute","tempmute","ipban","kick","unban","unmute","history")) {
            var pluginCommand = getCommand(key);
            if (pluginCommand != null) { pluginCommand.setExecutor(command); pluginCommand.setTabCompleter(command); }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(history, this);
        async(() -> {
            storage.initialize();
            ready.set(true);
            getLogger().info("Storage initialized; punishment checks are active.");
            return null;
        }, error -> { getLogger().log(Level.SEVERE,"Database initialization failed; joins will be denied until the configuration is fixed.",error); });
    }
    @Override public void onDisable() {
        ready.set(false);
        if (io != null) {
            io.shutdown();
            try { if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); io.shutdownNow(); }
        }
        if (storage != null) try { storage.close(); } catch (Exception ex) { getLogger().log(Level.WARNING,"Database close failed",ex); }
    }
    public Storage storage() { return storage; }
    public FileConfiguration menu() { return menu; }
    public boolean ready() { return ready.get(); }
    public HistoryGui history() { return history; }
    public void async(CheckedSupplier<?> operation, java.util.function.Consumer<Throwable> onError) {
        try { io.execute(() -> { try { operation.get(); } catch (Throwable ex) { onError.accept(ex); } }); }
        catch (RejectedExecutionException ex) { onError.accept(ex); }
    }
    @FunctionalInterface public interface CheckedSupplier<T> { T get() throws Exception; }
    public void main(Runnable work) { if (isEnabled()) Bukkit.getScheduler().runTask(this, work); }

    public Component color(String input) { return COLORS.deserialize(input == null ? "" : input); }
    public Component message(String key, Map<String,String> vars) {
        return color(MessageFormatter.replace(getConfig().getString("messages."+key,"&cMissing message: "+key), vars));
    }
    public void tell(CommandSender audience, String key, Map<String,String> vars) {
        String prefix = getConfig().getString("messages.prefix", "");
        audience.sendMessage(color(prefix).append(message(key, vars)));
    }
    public void tell(CommandSender audience, String key) { tell(audience, key, Map.of()); }

    public void updateMuteCache(UUID id, Punishment mute) { chatCache.put(id, new ChatState(mute, System.currentTimeMillis())); }
    public void invalidateMuteCache(UUID id) { if (id != null) chatCache.remove(id); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (!ready.get()) { event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,message("starting",Map.of())); return; }
        try {
            UUID id = event.getUniqueId(); String ip = event.getAddress().getHostAddress();
            List<Punishment> active = storage.findActive(id, ip, System.currentTimeMillis());
            for (Punishment p : active) {
                if (p.type().isBan() && p.isActive(System.currentTimeMillis())) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message("ban-screen",MessageFormatter.variables(p)));
                    return;
                }
            }
            storage.remember(id,event.getName(),getConfig().getBoolean("store-player-ips",true) ? ip : null);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Could not verify punishment for login: " + event.getName(),ex);
            if (getConfig().getBoolean("deny-logins-on-database-error",true))
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,message("database-unavailable",Map.of()));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (event.getPlayer().hasPermission("onrai.mutebypass")) return;
        UUID uuid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        ChatState state = chatCache.get(uuid);
        long refresh = Math.max(1, getConfig().getInt("chat-cache-seconds",15)) * 1000L;
        if (state != null && now - state.checkedAt() < refresh) {
            if (state.mute() != null && state.mute().isActive(now)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(message("mute-chat",MessageFormatter.variables(state.mute())));
            }
            return;
        }
        // Chat can be synchronous when another plugin triggers it. Never block the tick thread on SQL.
        if (!event.isAsynchronous()) {
            event.setCancelled(true);
            async(() -> { loadMute(uuid); return null; }, ex -> getLogger().log(Level.WARNING,"Mute refresh failed",ex));
            return;
        }
        try {
            Punishment mute = findMute(storage.findActive(uuid, null, now), now);
            updateMuteCache(uuid, mute);
            if (mute != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(message("mute-chat",MessageFormatter.variables(mute)));
            }
        } catch (Exception ex) {
            event.setCancelled(true); // fail closed: prevent a possible mute bypass on DB error
            getLogger().log(Level.WARNING,"Chat blocked: mute verification failed",ex);
            event.getPlayer().sendMessage(message("database-unavailable",Map.of()));
        }
    }
    public void loadMute(UUID id) throws Exception {
        Punishment mute = findMute(storage.findActive(id,null,System.currentTimeMillis()),System.currentTimeMillis());
        updateMuteCache(id,mute);
    }
    private static Punishment findMute(List<Punishment> list, long now) {
        for (Punishment p : list) if (p.type().isMute() && p.isActive(now)) return p;
        return null;
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        async(() -> { loadMute(id); return null; }, ex -> getLogger().log(Level.WARNING, "Could not load mute status", ex));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandWhileMuted(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission("onrai.mutebypass")) return;
        String raw = event.getMessage().substring(1).split(" ",2)[0].toLowerCase(Locale.ROOT);
        String command = raw.contains(":") ? raw.substring(raw.lastIndexOf(':')+1) : raw;
        if (!getConfig().getStringList("blocked-commands-while-muted").contains(command)) return;
        UUID id = event.getPlayer().getUniqueId();
        ChatState state = chatCache.get(id);
        long now = System.currentTimeMillis();
        if (state == null || now - state.checkedAt() >= Math.max(1,getConfig().getInt("chat-cache-seconds",15))*1000L) {
            event.setCancelled(true); // no synchronous database work on the tick thread
            async(() -> { loadMute(id); return null; }, ex -> getLogger().log(Level.WARNING,"Mute lookup failed",ex));
            event.getPlayer().sendMessage(message("mute-checking",Map.of()));
            return;
        }
        if (state.mute() != null && state.mute().isActive(now)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("mute-chat",MessageFormatter.variables(state.mute())));
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { invalidateMuteCache(event.getPlayer().getUniqueId()); }
}
