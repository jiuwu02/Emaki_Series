package emaki.jiuwu.craft.corelib.unlock;

public record UnlockSlotCost(
        String currencyProviderId,
        String currencyId,
        double currencyAmount,
        double currencyCeiling,
        String itemSourceToken,
        int itemAmount
) {

    public UnlockSlotCost {
        currencyProviderId = currencyProviderId == null ? "" : currencyProviderId;
        currencyId = currencyId == null ? "" : currencyId;
        itemSourceToken = itemSourceToken == null ? "" : itemSourceToken;
    }

    public boolean chargesCurrency() {
        return !currencyProviderId.isEmpty() && currencyAmount > 0.0D;
    }

    public boolean chargesItems() {
        return !itemSourceToken.isEmpty() && itemAmount > 0;
    }
}
