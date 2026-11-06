package dev.onrai.punishments.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);
    private Durations() {}

    /** Accepts e.g. 45m, 2d12h, 1w3d. Null means invalid. */
    public static Long parseMillis(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = TOKEN.matcher(text);
        long sum = 0;
        int offset = 0;
        try {
            while (m.find()) {
                if (m.start() != offset) return null;
                long n = Long.parseLong(m.group(1));
                long factor = switch (m.group(2).toLowerCase()) {
                    case "s" -> 1_000L;
                    case "m" -> 60_000L;
                    case "h" -> 3_600_000L;
                    case "d" -> 86_400_000L;
                    case "w" -> 604_800_000L;
                    default -> throw new IllegalArgumentException();
                };
                sum = Math.addExact(sum, Math.multiplyExact(n, factor));
                offset = m.end();
            }
            return offset == text.length() && sum > 0 ? sum : null;
        } catch (ArithmeticException | NumberFormatException e) { return null; }
    }

    public static String describe(long millis) {
        if (millis < 0) return "Permanent";
        if (millis <= 0) return "Expired";
        long s = (millis + 999L) / 1000;
        long days = s / 86400; s %= 86400;
        long hours = s / 3600; s %= 3600;
        long minutes = s / 60; s %= 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (minutes > 0) out.append(minutes).append("m ");
        if (s > 0 || out.isEmpty()) out.append(s).append("s ");
        return out.toString().trim();
    }
}
