package dev.onrai.punishments.command;

import dev.onrai.punishments.OnraiPunishments;
import dev.onrai.punishments.model.Punishment;
import dev.onrai.punishments.util.Addresses;
import dev.onrai.punishments.util.Durations;
import dev.onrai.punishments.util.MessageFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

public final class PunishmentCommands implements CommandExecutor, TabCompleter {
    private final OnraiPunishments plugin;
    private final IdentityResolver identities;
    public PunishmentCommands(OnraiPunishments plugin) { this.plugin = plugin; this.identities = new IdentityResolver(plugin); }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("onrai."+name)) { plugin.tell(sender,"no-permission"); return true; }
        if (!plugin.ready()) { plugin.tell(sender,"database-unavailable"); return true; }
        int min = (name.equals("tempban") || name.equals("tempmute")) ? 2 : 1;
        if (args.length < min) { plugin.tell(sender,"usage."+name); return true; }
        if (name.equals("history")) { plugin.history().open(sender,args[0],args.length > 1 ? args[1] : "1"); return true; }
        if (name.equals("kick")) { kick(sender,args); return true; }
        if (name.equals("unban") || name.equals("unmute")) { revoke(sender,args[0],name.equals("unmute")); return true; }
        punish(sender,name,args);
        return true;
    }
    private void kick(CommandSender sender, String[] args) {
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { plugin.tell(sender,"must-be-online"); return; }
        String reason = getReason(args,1);
        UUID id = target.getUniqueId(); String name = target.getName();
        String ip = target.getAddress() == null ? null : target.getAddress().getAddress().getHostAddress();
        Punishment punishment = new Punishment(UUID.randomUUID(),id,name,ip,Punishment.Type.KICK,
                sender.getName(),reason,System.currentTimeMillis(),-1,-1,null);
        plugin.async(() -> {
            plugin.storage().insert(punishment);
            plugin.main(() -> {
                Player live = Bukkit.getPlayer(id);
                if (live != null) live.kick(plugin.message("kick-screen",MessageFormatter.variables(punishment)));
                plugin.tell(sender,"punished",MessageFormatter.variables(punishment));
            });
            return null;
        }, err -> error(sender,err));
    }
    private void punish(CommandSender sender, String command, String[] args) {
        Punishment.Type type = switch (command) {
            case "ban" -> Punishment.Type.BAN;
            case "tempban" -> Punishment.Type.TEMP_BAN;
            case "mute" -> Punishment.Type.MUTE;
            case "tempmute" -> Punishment.Type.TEMP_MUTE;
            case "ipban" -> Punishment.Type.IP_BAN;
            default -> throw new IllegalArgumentException(command);
        };
        long until = -1;
        int reasonOffset = 1;
        if (type == Punishment.Type.TEMP_BAN || type == Punishment.Type.TEMP_MUTE) {
            Long period = Durations.parseMillis(args[1]);
            if (period == null) { plugin.tell(sender,"invalid-duration"); return; }
            try { until = Math.addExact(System.currentTimeMillis(),period); }
            catch (ArithmeticException e) { plugin.tell(sender,"invalid-duration"); return; }
            reasonOffset = 2;
        }
        final long expiresAt = until;
        String reason = getReason(args,reasonOffset);
        String literalIp = type == Punishment.Type.IP_BAN ? Addresses.parseLiteral(args[0]) : null;
        plugin.async(() -> {
            IdentityResolver.Identity target;
            if (literalIp != null) target = new IdentityResolver.Identity(null,args[0],literalIp);
            else {
                var resolved = identities.resolve(args[0]);
                if (resolved.isEmpty()) { plugin.main(() -> plugin.tell(sender,"unknown-player",Map.of("player",args[0]))); return null; }
                target = resolved.get();
            }
            if (type == Punishment.Type.IP_BAN && target.ip() == null) {
                plugin.main(() -> plugin.tell(sender,"ip-not-known",Map.of("player",args[0])));
                return null;
            }
            Punishment p = new Punishment(UUID.randomUUID(),target.id(),target.name(),type == Punishment.Type.IP_BAN ? target.ip() : null,
                    type,sender.getName(),reason,System.currentTimeMillis(),expiresAt,-1,null);
            plugin.storage().insert(p);
            plugin.main(() -> {
                if (target.id() != null) {
                    if (type.isMute()) plugin.updateMuteCache(target.id(),p);
                    Player online = Bukkit.getPlayer(target.id());
                    if (online != null && type.isBan()) online.kick(plugin.message("ban-screen",MessageFormatter.variables(p)));
                }
                // For an IP punishment, disconnect every connected player using that IP.
                if (type == Punishment.Type.IP_BAN) for (Player player : Bukkit.getOnlinePlayers()) {
                    String currentIp = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
                    if (target.ip().equals(currentIp)) player.kick(plugin.message("ban-screen",MessageFormatter.variables(p)));
                }
                plugin.tell(sender,"punished",MessageFormatter.variables(p));
            });
            return null;
        }, err -> error(sender,err));
    }
    private void revoke(CommandSender sender, String arg, boolean mute) {
        String ip = mute ? null : Addresses.parseLiteral(arg);
        plugin.async(() -> {
            IdentityResolver.Identity target;
            if (ip != null) target = new IdentityResolver.Identity(null,arg,ip);
            else {
                var resolved = identities.resolve(arg);
                if (resolved.isEmpty()) { plugin.main(() -> plugin.tell(sender,"unknown-player",Map.of("player",arg))); return null; }
                target = resolved.get();
            }
            int affected = plugin.storage().revoke(target.id(),target.ip(),mute,sender.getName(),System.currentTimeMillis());
            plugin.main(() -> {
                if (mute && target.id() != null) plugin.invalidateMuteCache(target.id());
                plugin.tell(sender, affected > 0 ? "revoked" : "nothing-to-revoke",
                        Map.of("player",arg,"count",String.valueOf(affected)));
            });
            return null;
        }, err -> error(sender,err));
    }
    private String getReason(String[] args,int offset) {
        if (args.length <= offset) return plugin.getConfig().getString("default-reason","No reason provided");
        String reason = String.join(" ",Arrays.copyOfRange(args,offset,args.length)).trim();
        return reason.length() > 300 ? reason.substring(0,300) : reason;
    }
    private void error(CommandSender sender,Throwable ex) {
        plugin.getLogger().log(Level.SEVERE,"Punishment command failed",ex);
        plugin.main(() -> plugin.tell(sender,"database-unavailable"));
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("onrai."+command.getName())) return List.of();
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).limit(30).toList();
        if (args.length == 2 && List.of("tempban","tempmute").contains(command.getName())) return List.of("10m","1h","1d","7d","30d");
        if (args.length == 2 && command.getName().equals("history")) return List.of("1","2","3");
        return List.of();
    }
}
