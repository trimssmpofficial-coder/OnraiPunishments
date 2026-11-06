package dev.onrai.punishments.model;

import java.util.UUID;

public record Punishment(
        UUID id, UUID targetUuid, String targetName, String targetIp, Type type,
        String actor, String reason, long createdAt, long expiresAt, long revokedAt, String revokedBy) {

    public enum Type {
        BAN, TEMP_BAN, MUTE, TEMP_MUTE, IP_BAN, KICK;
        public boolean isBan() { return this == BAN || this == TEMP_BAN || this == IP_BAN; }
        public boolean isMute() { return this == MUTE || this == TEMP_MUTE; }
    }

    public boolean isActive(long now) {
        return type != Type.KICK && revokedAt < 0 && (expiresAt < 0 || expiresAt > now);
    }
    public boolean isExpired(long now) { return expiresAt >= 0 && expiresAt <= now; }
    public String status(long now) {
        if (type == Type.KICK) return "Completed";
        if (revokedAt >= 0) return "Revoked";
        if (isExpired(now)) return "Expired";
        return "Active";
    }
}
