package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Objects;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record RepairMaterial(List<ItemSource> itemSources,
        int amount,
        String restoreRaw) {

    public RepairMaterial {
        itemSources = itemSources == null ? List.of() : itemSources.stream()
                .filter(Objects::nonNull)
                .toList();
        amount = Math.max(1, amount);
        restoreRaw = Texts.toStringSafe(restoreRaw).trim();
    }

    public boolean hasItemSources() {
        return !itemSources.isEmpty();
    }

    public boolean matches(ItemSource source) {
        if (source == null) {
            return false;
        }
        return itemSources.stream().anyMatch(expected -> ItemSourceUtil.matches(source, expected));
    }

    public String displaySources() {
        return itemSources.stream()
                .map(ItemSourceUtil::toShorthand)
                .filter(Texts::isNotBlank)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
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
