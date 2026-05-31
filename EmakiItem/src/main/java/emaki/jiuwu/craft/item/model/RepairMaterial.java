package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record RepairMaterial(String itemSource,
        int amount,
        String restoreRaw) {

    public RepairMaterial {
        itemSource = Texts.toStringSafe(itemSource);
        amount = Math.max(1, amount);
        restoreRaw = Texts.toStringSafe(restoreRaw).trim();
    }

    public boolean isPercent() {
        return restoreRaw.endsWith("%");
    }

    public int fixedValue() {
        Integer parsed = Numbers.tryParseInt(restoreRaw, null);
        return parsed == null ? 0 : Math.max(0, parsed);
    }

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

    public int resolveAmount(int maxDamage) {
        if (isPercent()) {
            return (int) Math.round(maxDamage * percent());
        }
        return fixedValue();
    }
}
