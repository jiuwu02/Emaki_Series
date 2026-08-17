package emaki.jiuwu.craft.corelib.unlock;

/**
 * Represents the cost for unlocking a single slot.
 * <p>
 * Bundles currency and item costs for one unlock ordinal.
 *
 * @param currencyProviderId the economy provider ID (empty = no currency charge)
 * @param currencyId         the currency identifier (may be empty for single-currency providers)
 * @param currencyAmount     the currency amount to charge (0 = no charge)
 * @param currencyCeiling    the maximum allowed amount (Double.MAX_VALUE = uncapped)
 * @param itemSourceToken    the item source token (empty = no item charge)
 * @param itemAmount         the number of items to consume (0 = no charge)
 */
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

    /** Returns true if this slot requires a currency payment. */
    public boolean chargesCurrency() {
        return !currencyProviderId.isEmpty() && currencyAmount > 0.0D;
    }

    /** Returns true if this slot requires item consumption. */
    public boolean chargesItems() {
        return !itemSourceToken.isEmpty() && itemAmount > 0;
    }
}
