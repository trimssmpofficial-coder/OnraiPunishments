package dev.onrai.punishments.gui;

import dev.onrai.punishments.OnraiPunishments;
import dev.onrai.punishments.command.*;
import dev.onrai.punishments.model.Punishment;
import dev.onrai.punishments.util.Addresses;
import dev.onrai.punishments.util.MessageFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

public final class HistoryGui implements Listener {
    private final OnraiPunishments plugin;
    public HistoryGui(OnraiPunishments plugin) { this.plugin = plugin; }
    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        private final String target;
        private final int page, pages;
        Holder(String target,int page,int pages) { this.target=target;this.page=page;this.pages=pages; }
        @Override public Inventory getInventory() { return inv; }
    }
    public void open(CommandSender sender,String target,String requestedPage) {
        if (!(sender instanceof Player viewer)) { plugin.tell(sender,"players-only"); return; }
        int page;
        try { page = Math.max(1,Integer.parseInt(requestedPage)); }
        catch (NumberFormatException ex) { plugin.tell(sender,"invalid-page"); return; }
        if (page > 100_000) { plugin.tell(sender,"invalid-page"); return; }
        FileConfiguration cfg = plugin.menu();
        int rows = Math.max(1,Math.min(6,cfg.getInt("rows",6)));
        List<Integer> slots = contentSlots(cfg,rows * 9);
        String ipLiteral = Addresses.parseLiteral(target);
        plugin.async(() -> {
            UUID uuid = null; String ip = null;
            if (ipLiteral != null) ip = ipLiteral;
            else {
                // Use already-seen identities; unknown online-mode names are resolved via Mojang by commands.
                var known = plugin.storage().lookupUuid(target);
                if (known.isPresent()) { uuid=known.get(); ip=plugin.storage().lookupIp(target).orElse(null); }
            }
            if (uuid == null && ip == null) { plugin.main(() -> plugin.tell(viewer,"unknown-player",Map.of("player",target))); return null; }
            long count = plugin.storage().historyCount(uuid,ip);
            int pages = (int)Math.max(1,(count + slots.size()-1)/slots.size());
            int actualPage = Math.min(page,pages);
            List<Punishment> history = plugin.storage().history(uuid,ip,(actualPage-1)*slots.size(),slots.size());
            plugin.main(() -> { if (viewer.isOnline()) show(viewer,target,actualPage,pages,rows,slots,history); });
            return null;
        }, err -> {
            plugin.getLogger().log(Level.WARNING,"Could not load history",err);
            plugin.main(() -> plugin.tell(viewer,"database-unavailable"));
        });
    }
    private List<Integer> contentSlots(FileConfiguration cfg,int size) {
        List<Integer> slots = new ArrayList<>();
        for (int n : cfg.getIntegerList("content-slots")) if (n >= 0 && n < size && !slots.contains(n)) slots.add(n);
        if (slots.isEmpty()) for(int i = 0;i<size;i++) if (i % 9 != 0 && i % 9 != 8 && i >= 9 && i < size-9) slots.add(i);
        return slots;
    }
    private void show(Player viewer,String target,int page,int pages,int rows,List<Integer> slots,List<Punishment> history) {
        FileConfiguration cfg = plugin.menu();
        Holder holder = new Holder(target,page,pages);
        String title = MessageFormatter.replace(cfg.getString("title","&8History: {player} &7({page}/{pages})"),
                Map.of("player",target,"page",String.valueOf(page),"pages",String.valueOf(pages)));
        Inventory inventory = Bukkit.createInventory(holder,rows * 9,plugin.color(title));
        holder.inv = inventory;
        if (cfg.getBoolean("filler.enabled",true)) {
            ItemStack filler = item(cfg.getString("filler.material","GRAY_STAINED_GLASS_PANE"),cfg.getString("filler.name"," "),List.of(),Map.of());
            for (int i=0;i<inventory.getSize();i++) inventory.setItem(i,filler);
        }
        for(int i=0;i<history.size() && i<slots.size();i++) {
            Punishment p = history.get(i);
            String key = "types."+p.type().name().toLowerCase(Locale.ROOT);
            List<String> lore = cfg.getStringList("punishment-lore");
            inventory.setItem(slots.get(i),item(cfg.getString(key+".material","PAPER"),
                    cfg.getString(key+".name","&e{type}"),lore,MessageFormatter.variables(p)));
        }
        Map<String,String> nav = Map.of("page",String.valueOf(page),"pages",String.valueOf(pages),"player",target);
        if (page > 1) place(inventory,cfg.getInt("previous.slot",45),item(cfg.getString("previous.material","ARROW"),cfg.getString("previous.name","&ePrevious page"),List.of(),nav));
        if (page < pages) place(inventory,cfg.getInt("next.slot",53),item(cfg.getString("next.material","ARROW"),cfg.getString("next.name","&eNext page"),List.of(),nav));
        place(inventory,cfg.getInt("close.slot",49),item(cfg.getString("close.material","BARRIER"),cfg.getString("close.name","&cClose"),List.of(),nav));
        viewer.openInventory(inventory);
    }
    private void place(Inventory inv,int slot,ItemStack item) { if (slot>=0 && slot<inv.getSize()) inv.setItem(slot,item); }
    private ItemStack item(String material,String title,List<String> lore,Map<String,String> vars) {
        Material mat = Material.matchMaterial(material);
        ItemStack stack = new ItemStack(mat == null || !mat.isItem() ? Material.PAPER : mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plugin.color(MessageFormatter.replace(title,vars)));
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        for(String line:lore) lines.add(plugin.color(MessageFormatter.replace(line,vars)));
        meta.lore(lines); stack.setItemMeta(meta); return stack;
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        FileConfiguration cfg = plugin.menu();
        if (slot == cfg.getInt("close.slot",49)) { player.closeInventory(); return; }
        if (slot == cfg.getInt("previous.slot",45) && holder.page > 1) open(player,holder.target,String.valueOf(holder.page-1));
        if (slot == cfg.getInt("next.slot",53) && holder.page < holder.pages) open(player,holder.target,String.valueOf(holder.page+1));
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
