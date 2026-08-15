package emaki.jiuwu.craft.station.gui;

import java.util.Locale;

public final class DurationDisplay {

    private DurationDisplay() {
    }

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
