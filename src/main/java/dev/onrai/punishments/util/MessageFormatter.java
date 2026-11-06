package dev.onrai.punishments.util;

import dev.onrai.punishments.model.Punishment;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of("UTC"));
    private MessageFormatter() {}
    public static String replace(String value, Map<String, String> fields) {
        String output = value;
        for (var e : fields.entrySet()) output = output.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        return output;
    }
    public static Map<String, String> variables(Punishment p) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("player", p.targetName() == null ? (p.targetIp() == null ? "Unknown" : p.targetIp()) : p.targetName());
        map.put("type", p.type().name().replace('_', ' '));
        map.put("staff", p.actor());
        map.put("reason", p.reason());
        map.put("date", DATE.format(Instant.ofEpochMilli(p.createdAt())));
        map.put("duration", p.expiresAt() < 0 ? "Permanent" : Durations.describe(Math.max(0, p.expiresAt() - p.createdAt())));
        map.put("remaining", p.expiresAt() < 0 ? "Permanent" : Durations.describe(Math.max(0, p.expiresAt() - System.currentTimeMillis())));
        map.put("status", p.status(System.currentTimeMillis()));
        map.put("id", p.id().toString());
        return map;
    }
}
