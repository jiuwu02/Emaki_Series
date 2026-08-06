package emaki.jiuwu.craft.station.gui;

import java.util.Locale;

/**
 * Formats queue durations for display.
 *
 * <p>Lives on its own because three renderers and the command router all need the same format, and a queue
 * entry that reads {@code 2:05} in chat but differently in a GUI would look like two different entries.
 */
public final class DurationDisplay {

    private DurationDisplay() {
    }

    /**
     * Formats a duration as {@code h:mm:ss} or {@code m:ss}.
     *
     * @param millis the duration; negatives are treated as zero
     * @return the formatted text
     */
    public static String format(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
