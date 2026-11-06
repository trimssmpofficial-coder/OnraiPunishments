package dev.onrai.punishments.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.onrai.punishments.model.Punishment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlStorage implements Storage {
    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final boolean sqlite;
    private HikariDataSource pool;

    public SqlStorage(JavaPlugin plugin, boolean sqlite) {
        this.plugin = plugin; this.config = plugin.getConfig(); this.sqlite = sqlite;
    }

    @Override public void initialize() throws Exception {
        HikariConfig cfg = new HikariConfig();
        if (sqlite) {
            File file = new File(plugin.getDataFolder(), config.getString("database.sqlite.file", "punishments.db"));
            File canonical = file.getCanonicalFile();
            if (!canonical.toPath().startsWith(plugin.getDataFolder().getCanonicalFile().toPath()))
                throw new IllegalArgumentException("SQLite file must be inside the plugin folder");
            canonical.getParentFile().mkdirs();
            cfg.setJdbcUrl("jdbc:sqlite:" + canonical);
            cfg.setDriverClassName("org.sqlite.JDBC");
            cfg.setMaximumPoolSize(1); // SQLite permits only one writer
            cfg.setConnectionInitSql("PRAGMA busy_timeout=5000");
        } else {
            String host = config.getString("database.mysql.host", "127.0.0.1");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "punishments");
            if (!host.matches("[A-Za-z0-9.:-]+") || !database.matches("[A-Za-z0-9_]+") || port < 1 || port > 65535)
                throw new IllegalArgumentException("Invalid MySQL host, port or database");
            cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + config.getBoolean("database.mysql.ssl", false) + "&characterEncoding=UTF-8");
            cfg.setUsername(config.getString("database.mysql.username", "root"));
            cfg.setPassword(config.getString("database.mysql.password", ""));
            cfg.setMaximumPoolSize(Math.max(2, Math.min(16, config.getInt("database.mysql.pool-size", 6))));
            cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
        cfg.setPoolName("OnraiPunishments");
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(5000);
        cfg.setValidationTimeout(2000);
        pool = new HikariDataSource(cfg);
        try (Connection con = pool.getConnection(); Statement st = con.createStatement()) {
            if (sqlite) st.execute("PRAGMA journal_mode=WAL");
            st.execute("CREATE TABLE IF NOT EXISTS punishment_profiles (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16) NOT NULL, name_lower VARCHAR(16) NOT NULL, ip VARCHAR(45), last_seen BIGINT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS punishments (id VARCHAR(36) PRIMARY KEY, target_uuid VARCHAR(36), target_name VARCHAR(16), target_ip VARCHAR(45), type VARCHAR(20) NOT NULL, actor VARCHAR(64) NOT NULL, reason TEXT NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, revoked_at BIGINT NOT NULL, revoked_by VARCHAR(64))");
            ensureIndex(st, "CREATE INDEX idx_profiles_name ON punishment_profiles(name_lower)");
            ensureIndex(st, "CREATE INDEX idx_punishments_uuid ON punishments(target_uuid, created_at)");
            ensureIndex(st, "CREATE INDEX idx_punishments_ip ON punishments(target_ip, type)");
        } catch (Exception ex) { close(); throw ex; }
    }
    private void ensureIndex(Statement st, String query) throws SQLException {
        try { st.execute(query); } catch (SQLException ex) {
            String state = ex.getSQLState();
            String message = ex.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (!("42S11".equals(state) || message.contains("already exists") || message.contains("duplicate key name"))) throw ex;
        }
    }
    @Override public void remember(UUID id, String name, String ip) throws Exception {
        String query = sqlite
            ? "INSERT INTO punishment_profiles(uuid,name,name_lower,ip,last_seen) VALUES(?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, name_lower=excluded.name_lower, ip=excluded.ip, last_seen=excluded.last_seen"
            : "INSERT INTO punishment_profiles(uuid,name,name_lower,ip,last_seen) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name), name_lower=VALUES(name_lower), ip=VALUES(ip), last_seen=VALUES(last_seen)";
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, id.toString()); ps.setString(2, name);
            ps.setString(3, name.toLowerCase(java.util.Locale.ROOT)); ps.setString(4, ip);
            ps.setLong(5, System.currentTimeMillis()); ps.executeUpdate();
        }
    }
    @Override public Optional<UUID> lookupUuid(String name) throws Exception {
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT uuid FROM punishment_profiles WHERE name_lower=? ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(UUID.fromString(rs.getString(1)));
            }
        }
        // A never-joined player can still have a punishment created through Mojang UUID lookup.
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement(
                "SELECT target_uuid FROM punishments WHERE LOWER(target_name)=? AND target_uuid IS NOT NULL ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }
    @Override public Optional<String> lookupIp(String name) throws Exception {
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT ip FROM punishment_profiles WHERE name_lower=? ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty(); }
        }
    }
    @Override public void insert(Punishment p) throws Exception {
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement(
                "INSERT INTO punishments(id,target_uuid,target_name,target_ip,type,actor,reason,created_at,expires_at,revoked_at,revoked_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1,p.id().toString()); ps.setString(2, uuid(p.targetUuid()));
            ps.setString(3,p.targetName()); ps.setString(4,p.targetIp()); ps.setString(5,p.type().name());
            ps.setString(6,p.actor()); ps.setString(7,p.reason()); ps.setLong(8,p.createdAt());
            ps.setLong(9,p.expiresAt()); ps.setLong(10,p.revokedAt()); ps.setString(11,p.revokedBy());
            ps.executeUpdate();
        }
    }
    private static String uuid(UUID u) { return u == null ? null : u.toString(); }
    private static Punishment map(ResultSet r) throws SQLException {
        String targetUuid = r.getString("target_uuid");
        return new Punishment(UUID.fromString(r.getString("id")), targetUuid == null ? null : UUID.fromString(targetUuid),
                r.getString("target_name"), r.getString("target_ip"), Punishment.Type.valueOf(r.getString("type")),
                r.getString("actor"), r.getString("reason"), r.getLong("created_at"), r.getLong("expires_at"),
                r.getLong("revoked_at"), r.getString("revoked_by"));
    }
    @Override public List<Punishment> findActive(UUID id, String ip, long now) throws Exception {
        String query = "SELECT * FROM punishments WHERE revoked_at < 0 AND (expires_at < 0 OR expires_at > ?) AND ((target_uuid = ? AND type <> 'IP_BAN') OR (target_ip = ? AND type='IP_BAN')) ORDER BY created_at DESC";
        return select(query, ps -> { ps.setLong(1,now); ps.setString(2, uuid(id)); ps.setString(3,ip); });
    }
    @Override public List<Punishment> history(UUID id, String ip, int offset, int limit) throws Exception {
        String query = "SELECT * FROM punishments WHERE target_uuid = ? OR (target_ip = ? AND type='IP_BAN') ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return select(query, ps -> { ps.setString(1,uuid(id)); ps.setString(2,ip); ps.setInt(3,limit); ps.setInt(4,offset); });
    }
    @Override public long historyCount(UUID id, String ip) throws Exception {
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM punishments WHERE target_uuid = ? OR (target_ip = ? AND type='IP_BAN')")) {
            ps.setString(1,uuid(id)); ps.setString(2,ip);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }
    @Override public int revoke(UUID id, String ip, boolean mute, String actor, long now) throws Exception {
        String types = mute ? "'MUTE','TEMP_MUTE'" : "'BAN','TEMP_BAN','IP_BAN'";
        // Unban by username must also revoke historical IP bans attached to that UUID,
        // even when the player's current IP address has since changed.
        String query = "UPDATE punishments SET revoked_at=?, revoked_by=? WHERE revoked_at < 0 AND (expires_at < 0 OR expires_at > ?) AND type IN ("+types+") AND (target_uuid=? OR (target_ip=? AND type='IP_BAN'))";
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
            ps.setLong(1,now); ps.setString(2,actor); ps.setLong(3,now); ps.setString(4,uuid(id)); ps.setString(5,ip);
            return ps.executeUpdate();
        }
    }
    private interface Setter { void accept(PreparedStatement ps) throws SQLException; }
    private List<Punishment> select(String query, Setter setter) throws Exception {
        try (Connection con = pool.getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
            setter.accept(ps);
            List<Punishment> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
            return list;
        }
    }
    @Override public void close() { if (pool != null) pool.close(); }
}
