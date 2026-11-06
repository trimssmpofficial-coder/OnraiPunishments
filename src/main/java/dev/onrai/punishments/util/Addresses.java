package dev.onrai.punishments.util;

import java.net.InetAddress;

public final class Addresses {
    private Addresses() {}
    /** Only literal IP addresses. Reject DNS names to avoid unexpected resolution. */
    public static String parseLiteral(String raw) {
        if (raw == null || raw.isBlank() || !raw.matches("[0-9a-fA-F.:]+")) return null;
        // Avoid InetAddress treating short numeric strings or hex names as hostnames.
        if (!raw.contains(":") && !raw.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
        try {
            InetAddress resolved = InetAddress.getByName(raw);
            String result = resolved.getHostAddress();
            if (result.contains("%")) return null;
            return result;
        } catch (Exception ex) { return null; }
    }
}
