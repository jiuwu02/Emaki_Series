package emaki.jiuwu.craft.strengthen.model;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * A single cost line of a strengthen attempt.
 *
 * <p>Describes who provides the currency (e.g. {@code "items"}, {@code "vault"},
 * an economy provider id), the specific currency id, a display name and the
 * amount required.
 *
 * @param provider    the cost provider id (lower-cased)
 * @param currencyId  the currency identifier within the provider
 * @param displayName a human-readable label for the cost
 * @param amount      the required amount; clamped to {@code >= 0}
 */
public record AttemptCost(String provider,
        String currencyId,
        String displayName,
        long amount) {

    /** Canonical constructor; normalizes text fields and clamps the amount. */
    public AttemptCost {
        provider = Texts.lower(provider);
        currencyId = Texts.toStringSafe(currencyId);
        displayName = Texts.toStringSafe(displayName);
        amount = Math.max(0L, amount);
    }

    /** {@return whether this cost is paid with items rather than a currency} */
    public boolean itemCost() {
        return "items".equals(provider);
    }
}
