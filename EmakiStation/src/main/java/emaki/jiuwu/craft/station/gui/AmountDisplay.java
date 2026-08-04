package emaki.jiuwu.craft.station.gui;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Formats {@code long} amounts for display.
 *
 * <h2>Why this class exists as display-only</h2>
 * A stored amount can be far larger than any {@link org.bukkit.inventory.ItemStack} can carry, so the
 * number and the item are deliberately kept apart:
 *
 * <ul>
 *   <li>A rendered stack's {@code amount} only ever uses 1..99. Java Edition's {@code max_stack_size}
 *       component is only valid in that range, so trying to push a {@code long} into a stack is not a
 *       display compromise, it is invalid data.</li>
 *   <li>The real number lives in lore: {@link #compact(long)} for a glanceable form and
 *       {@link #precise(long)} for the exact value.</li>
 *   <li><strong>A rendered stack must never be handed to a player.</strong> It carries display lore and a
 *       rewritten stack-size component; giving it away burns that presentation into a real item
 *       permanently. Delivery rebuilds stacks from the data layer and splits them by the item's own
 *       maximum stack size instead.</li>
 * </ul>
 */
public final class AmountDisplay {

    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;
    private static final long TRILLION = 1_000_000_000_000L;

    /** Largest amount a rendered stack may claim; vanilla rejects anything above this. */
    public static final int MAX_RENDERED_STACK = 99;

    private AmountDisplay() {
    }

    /**
     * Renders a compact, glanceable form such as {@code 1.2M}.
     *
     * @param amount the amount; negatives are treated as zero
     * @return the compact text
     */
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

    /**
     * Renders the exact amount with thousands separators.
     *
     * @param amount the amount; negatives are treated as zero
     * @return the precise text
     */
    public static String precise(long amount) {
        return new DecimalFormat("#,##0").format(Math.max(0L, amount));
    }

    /**
     * Clamps an amount into the range a rendered stack may legally claim.
     *
     * @param amount the real amount
     * @return a stack size in 1..99
     */
    public static int renderedStackSize(long amount) {
        if (amount <= 1L) {
            return 1;
        }
        return (int) Math.min(amount, MAX_RENDERED_STACK);
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
