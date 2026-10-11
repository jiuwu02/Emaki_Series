package emaki.jiuwu.craft.structure.config;

import java.time.Duration;
import java.util.Locale;

public final class Durations {

    private static final long MAX_SECONDS = Duration.ofDays(3650).toSeconds();
    private static final long MAX_MILLIS = Duration.ofDays(3650).toMillis();

    private Durations() {
    }

    public static boolean isValid(String raw) {
        return resolve(raw) != null;
    }

    public static Duration parse(String raw, Duration fallback) {
        Duration resolved = resolve(raw);
        return resolved == null ? fallback : resolved;
    }

    private static Duration resolve(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        char last = text.charAt(text.length() - 1);
        long multiplier;
        String digits;
        if (text.endsWith("ms")) {
            String millis = text.substring(0, text.length() - 2);
            if (millis.isEmpty()) {
                return null;
            }
            long value;
            try {
                value = Long.parseLong(millis.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
            if (value < 0L || value > MAX_MILLIS) {
                return null;
            }
            return Duration.ofMillis(value);
        }
        switch (last) {
            case 's' -> {
                multiplier = 1L;
                digits = text.substring(0, text.length() - 1);
            }
            case 'm' -> {
                multiplier = 60L;
                digits = text.substring(0, text.length() - 1);
            }
            case 'h' -> {
                multiplier = 3600L;
                digits = text.substring(0, text.length() - 1);
            }
            case 'd' -> {
                multiplier = 86400L;
                digits = text.substring(0, text.length() - 1);
            }
            default -> {
                multiplier = 1L;
                digits = text;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        long seconds;
        try {
            seconds = Long.parseLong(digits.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
        if (seconds < 0L || seconds > MAX_SECONDS) {
            return null;
        }
        if (multiplier > 1L && seconds > MAX_SECONDS / multiplier) {
            return null;
        }
        return Duration.ofSeconds(seconds * multiplier);
    }
}
