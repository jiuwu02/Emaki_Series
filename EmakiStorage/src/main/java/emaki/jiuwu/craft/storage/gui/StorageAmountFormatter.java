package emaki.jiuwu.craft.storage.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.storage.config.AppConfig;

/**
 * Renders stored amounts for display.
 *
 * <p>Two independent representations are produced:
 *
 * <ul>
 *   <li>the item's {@code amount} field, scaled to {@code 1..99} to express occupancy. Java
 *       Edition's {@code max_stack_size} component is only valid in {@code 1..99}, so nothing
 *       above 99 is ever used and no packet trickery is involved.</li>
 *   <li>the exact figure written into lore, using compact units plus a grouped exact number.</li>
 * </ul>
 *
 * <p>Scale boundaries matter: any non-zero amount shows at least 1, a partially filled slot shows
 * at most 98, and only a genuinely full slot shows 99. Without those clamps a nearly-empty slot
 * would render as 0 (invisible) and a nearly-full one would be indistinguishable from full.
 */
public final class StorageAmountFormatter {

    private static final long[] UNIT_THRESHOLDS = {
        1_000L,
        1_000_000L,
        1_000_000_000L,
        1_000_000_000_000L,
        1_000_000_000_000_000L,
        1_000_000_000_000_000_000L
    };

    private volatile AppConfig.DisplayConfig config;

    public StorageAmountFormatter(AppConfig.DisplayConfig config) {
        this.config = config == null ? AppConfig.DisplayConfig.defaults() : config;
    }

    public void reconfigure(AppConfig.DisplayConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    /**
     * Computes the item amount used to express occupancy.
     *
     * @param amount     the stored amount
     * @param stackLimit the effective ceiling; {@link Long#MAX_VALUE} means unlimited
     * @return a value in {@code 1..percent_scale}
     */
    public int displayAmount(long amount, long stackLimit) {
        AppConfig.DisplayConfig active = config;
        int scale = clampScale(active.percentScale());
        if (active.amountMode() == AppConfig.AmountMode.ONE) {
            return 1;
        }
        // An unlimited ceiling makes a percentage meaningless, so display degrades to a flat 1.
        if (stackLimit >= Long.MAX_VALUE || stackLimit <= 0L) {
            return 1;
        }
        if (amount <= 0L) {
            return 1;
        }
        if (amount >= stackLimit) {
            return scale;
        }
        long scaled = Math.round((double) amount / (double) stackLimit * scale);
        int clamped = (int) Math.max(1L, Math.min(scale - 1L, scaled));
        return clamped;
    }

    /**
     * {@return the true occupancy percentage, computed on the real 0-100 range}
     *
     * <p>Deliberately independent of the 1..99 item scale so lore never inherits the clamping.
     */
    public double occupancyPercent(long amount, long stackLimit) {
        if (stackLimit >= Long.MAX_VALUE || stackLimit <= 0L || amount <= 0L) {
            return 0.0D;
        }
        return Math.min(100.0D, (double) amount / (double) stackLimit * 100.0D);
    }

    /**
     * Formats an amount with compact units.
     *
     * <p>{@code Long.MAX_VALUE} is roughly {@code 9.22E}, so the {@code E} unit covers the entire
     * {@code long} range. Trailing zeroes are trimmed, giving {@code 1.5K} rather than
     * {@code 1.50K}.
     *
     * @param amount the value to format
     * @return the compact string, or the plain number when compact units are disabled
     */
    public String compact(long amount) {
        AppConfig.DisplayConfig active = config;
        List<String> units = active.compactUnits();
        if (units.isEmpty() || amount < UNIT_THRESHOLDS[0]) {
            return String.valueOf(amount);
        }
        int index = -1;
        for (int candidate = 0; candidate < UNIT_THRESHOLDS.length && candidate < units.size(); candidate++) {
            if (amount >= UNIT_THRESHOLDS[candidate]) {
                index = candidate;
            }
        }
        if (index < 0) {
            return String.valueOf(amount);
        }
        BigDecimal value = BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(UNIT_THRESHOLDS[index]), Math.max(0, active.compactDecimals()),
                        RoundingMode.DOWN)
                .stripTrailingZeros();
        return value.toPlainString() + units.get(index);
    }

    /** {@return the amount with digit grouping, for the exact lore line} */
    public String exact(long amount) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount);
    }

    /** {@return the ceiling rendered compactly, or an empty string when unlimited} */
    public String compactLimit(long stackLimit) {
        if (stackLimit >= Long.MAX_VALUE || stackLimit <= 0L) {
            return "";
        }
        return compact(stackLimit);
    }

    /** {@return the occupancy percentage rendered with one decimal place} */
    public String percentText(long amount, long stackLimit) {
        double percent = occupancyPercent(amount, stackLimit);
        if (percent <= 0.0D) {
            return "0";
        }
        BigDecimal value = BigDecimal.valueOf(percent)
                .setScale(1, RoundingMode.DOWN)
                .stripTrailingZeros();
        return value.toPlainString();
    }

    /** {@return whether percentage display is meaningful for this ceiling} */
    public boolean percentMeaningful(long stackLimit) {
        return config.amountMode() == AppConfig.AmountMode.PERCENT
                && stackLimit > 0L
                && stackLimit < Long.MAX_VALUE;
    }

    /** {@return whether the exact line should be appended} */
    public boolean showExactAmount() {
        return config.showExactAmount();
    }

    /** {@return where generated lines go relative to the template lore} */
    public AppConfig.LorePosition lorePosition() {
        return config.lorePosition();
    }

    /** {@return the clamped display scale; the vanilla component only accepts {@code 1..99}} */
    public int clampScale(int configured) {
        return Math.max(1, Math.min(99, configured));
    }
}
