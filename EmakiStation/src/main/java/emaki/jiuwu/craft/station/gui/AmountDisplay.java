package emaki.jiuwu.craft.station.gui;

import java.text.DecimalFormat;
import java.util.Locale;

public final class AmountDisplay {

    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;
    private static final long TRILLION = 1_000_000_000_000L;

    public static final int MAX_RENDERED_STACK = 99;

    private AmountDisplay() {
    }

    public static String compact(long amount) {
        long safe = Math.max(0L, amount);
        if (safe < THOUSAND) {
            return Long.toString(safe);
        }
        if (safe < MILLION) {
            return scaled(safe, THOUSAND, "K");
        }
        if (safe < BILLION) {
            return scaled(safe, MILLION, "M");
        }
        if (safe < TRILLION) {
            return scaled(safe, BILLION, "B");
        }
        return scaled(safe, TRILLION, "T");
    }

    public static String precise(long amount) {
        return new DecimalFormat("#,##0").format(Math.max(0L, amount));
    }

    public static int renderedStackSize(long amount) {
        if (amount <= 1L) {
            return 1;
        }
        return (int) Math.min(amount, MAX_RENDERED_STACK);
    }

    public static int previewStackSize(long amount) {
        if (amount <= 1L) {
            return 1;
        }
        return amount <= MAX_RENDERED_STACK ? (int) amount : 1;
    }

    public static boolean showsStackNumber(long amount) {
        return amount > 1L && amount <= MAX_RENDERED_STACK;
    }

    private static String scaled(long amount, long unit, String suffix) {
        double value = (double) amount / unit;
        if (value >= 100.0D) {
            return String.format(Locale.ROOT, "%.0f%s", value, suffix);
        }
        if (value >= 10.0D) {
            return String.format(Locale.ROOT, "%.1f%s", value, suffix);
        }
        return String.format(Locale.ROOT, "%.2f%s", value, suffix);
    }
}
