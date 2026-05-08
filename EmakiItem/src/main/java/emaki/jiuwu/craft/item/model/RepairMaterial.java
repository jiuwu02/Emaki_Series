package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * A single repair material entry defining what item can repair and how much durability it restores.
 */
public record RepairMaterial(String itemSource,
        int amount,
        String restoreRaw) {

    public RepairMaterial {
        itemSource = Texts.toStringSafe(itemSource);
        amount = Math.max(1, amount);
        restoreRaw = Texts.toStringSafe(restoreRaw).trim();
    }

    /**
     * Whether the restore value is a percentage of max_damage.
     */
    public boolean isPercent() {
        return restoreRaw.endsWith("%");
    }

    /**
     * Get the fixed restore value (only valid when {@link #isPercent()} is false).
     */
    public int fixedValue() {
        Integer parsed = Numbers.tryParseInt(restoreRaw, null);
        return parsed == null ? 0 : Math.max(0, parsed);
    }

    /**
     * Get the percentage value as a fraction (0.0~1.0).
     */
    public double percent() {
        if (!isPercent()) {
            return 0D;
        }
        String numericPart = restoreRaw.substring(0, restoreRaw.length() - 1).trim();
        Double parsed = Numbers.tryParseDouble(numericPart, null);
        if (parsed == null) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, parsed / 100D));
    }

    /**
     * Resolve the actual durability restore amount based on the item's max damage.
     *
     * @param maxDamage the maximum damage value of the item being repaired
     * @return the amount of durability to restore
     */
    public int resolveAmount(int maxDamage) {
        if (isPercent()) {
            return (int) Math.round(maxDamage * percent());
        }
        return fixedValue();
    }
}
