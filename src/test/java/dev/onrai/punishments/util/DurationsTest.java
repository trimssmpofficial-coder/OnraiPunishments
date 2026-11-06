package dev.onrai.punishments.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DurationsTest {
    @Test void parsesCompoundDurations() {
        assertEquals(5_400_000L, Durations.parseMillis("1h30m"));
        assertEquals(691_200_000L, Durations.parseMillis("1w1d"));
        assertEquals(45_000L, Durations.parseMillis("45s"));
    }
    @Test void rejectsInvalidDurations() {
        assertNull(Durations.parseMillis(""));
        assertNull(Durations.parseMillis("2x"));
        assertNull(Durations.parseMillis("1h bananas"));
        assertNull(Durations.parseMillis("0s"));
        assertNull(Durations.parseMillis("999999999999999999999w"));
    }
}
