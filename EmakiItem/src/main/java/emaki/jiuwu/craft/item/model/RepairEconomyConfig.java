package emaki.jiuwu.craft.item.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record RepairEconomyConfig(boolean enabled,
        String restoreRaw,
        List<RepairCurrencyCost> currencies) {

    public RepairEconomyConfig {
        restoreRaw = Texts.toStringSafe(restoreRaw).trim();
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
    }

    public static RepairEconomyConfig disabled() {
        return new RepairEconomyConfig(false, "", List.of());
    }

    public boolean hasCurrencies() {
        return !currencies.isEmpty();
    }

    public boolean isPercent() {
        return restoreRaw.endsWith("%");
    }

    public double percent() {
        if (!isPercent()) {
            return 0D;
        }
        Double parsed = emaki.jiuwu.craft.corelib.api.math.Numbers.tryParseDouble(restoreRaw.substring(0, restoreRaw.length() - 1).trim(), null);
        return parsed == null ? 0D : Math.max(0D, Math.min(1D, parsed / 100D));
    }

    public int fixedValue() {
        Integer parsed = emaki.jiuwu.craft.corelib.api.math.Numbers.tryParseInt(restoreRaw, null);
        return parsed == null ? 0 : Math.max(0, parsed);
    }

    public int resolveAmount(int maxDamage) {
        if (isPercent()) {
            return (int) Math.round(maxDamage * percent());
        }
        return fixedValue();
    }
}
