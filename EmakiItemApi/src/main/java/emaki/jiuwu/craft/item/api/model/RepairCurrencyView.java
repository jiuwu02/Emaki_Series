package emaki.jiuwu.craft.item.api.model;

import org.jetbrains.annotations.NotNull;

/** One currency component of an economy repair quote. */
public record RepairCurrencyView(@NotNull String provider,
                                 @NotNull String currencyId,
                                 @NotNull String displayName,
                                 double requiredAmount,
                                 double availableAmount,
                                 boolean supported) {

    public RepairCurrencyView {
        provider = provider == null ? "" : provider;
        currencyId = currencyId == null ? "" : currencyId;
        displayName = displayName == null ? "" : displayName;
        requiredAmount = Math.max(0D, requiredAmount);
        availableAmount = Math.max(0D, availableAmount);
    }

    /** {@return whether this currency is supported and its balance covers the quote} */
    public boolean affordable() {
        return supported && availableAmount + 1.0E-9D >= requiredAmount;
    }
}
