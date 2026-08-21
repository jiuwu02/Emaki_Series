package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public record RepairMaterial(List<ItemSourceRef> itemSources,
        int amount,
        String restoreRaw,
        @Nullable Matcher matcher) {

    public RepairMaterial {
        itemSources = itemSources == null ? List.of() : itemSources.stream()
                .filter(Objects::nonNull)
                .toList();
        amount = Math.max(1, amount);
        restoreRaw = Texts.toStringSafe(restoreRaw).trim();
    }

    public RepairMaterial(List<ItemSourceRef> itemSources, int amount, String restoreRaw) {
        this(itemSources, amount, restoreRaw, null);
    }

    public boolean hasItemSources() {
        return !itemSources.isEmpty();
    }

    public boolean hasMatcher() {
        return matcher != null;
    }

    public boolean matches(ItemSourceRef source) {
        if (source == null) {
            return false;
        }
        return itemSources.stream().anyMatch(expected -> ItemSourceUtil.matches(source, expected));
    }

    public boolean matches(MatchContext context) {
        if (context == null) {
            return false;
        }
        if (matcher != null) {
            return matcher.test(context);
        }
        return matches(context.itemSource());
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
