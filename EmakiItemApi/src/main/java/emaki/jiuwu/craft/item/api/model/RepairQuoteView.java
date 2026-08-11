package emaki.jiuwu.craft.item.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/** Economy-backed repair quote produced by the runtime service. */
public record RepairQuoteView(@NotNull String itemId,
                              int currentDamage,
                              int maxDamage,
                              int restoreAmount,
                              @NotNull String reasonKey,
                              @NotNull List<RepairCurrencyView> currencies) {

    public RepairQuoteView {
        itemId = itemId == null ? "" : itemId;
        currentDamage = Math.max(0, currentDamage);
        maxDamage = Math.max(0, maxDamage);
        restoreAmount = Math.max(0, restoreAmount);
        reasonKey = reasonKey == null ? "" : reasonKey;
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
    }

    /** {@return whether every quoted currency is supported and affordable} */
    public boolean affordable() {
        return reasonKey.isEmpty()
                && !currencies.isEmpty()
                && currencies.stream().allMatch(RepairCurrencyView::affordable);
    }
}
