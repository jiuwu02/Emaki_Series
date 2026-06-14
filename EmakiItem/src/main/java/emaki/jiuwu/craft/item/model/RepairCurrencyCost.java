package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record RepairCurrencyCost(String provider,
        String currencyId,
        double amount,
        double baseCost,
        String costFormula,
        String displayName) {

    public RepairCurrencyCost {
        provider = Texts.isBlank(provider) ? "auto" : Texts.lower(provider);
        currencyId = Texts.toStringSafe(currencyId);
        amount = Math.max(0D, amount);
        baseCost = Math.max(0D, baseCost);
        costFormula = Texts.toStringSafe(costFormula).trim();
        displayName = Texts.toStringSafe(displayName);
    }

    public boolean hasCost() {
        return amount > 0D || baseCost > 0D || Texts.isNotBlank(costFormula);
    }

    public String effectiveDisplayName() {
        if (Texts.isNotBlank(displayName)) {
            return displayName;
        }
        return Texts.isBlank(currencyId) ? provider : currencyId;
    }
}
