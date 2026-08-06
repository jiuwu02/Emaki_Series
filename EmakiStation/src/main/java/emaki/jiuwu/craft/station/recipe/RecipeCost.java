package emaki.jiuwu.craft.station.recipe;

import java.util.Locale;

/**
 * The currency a recipe charges per batch.
 *
 * <h2>Why the provider id is stored as a plain string</h2>
 * CoreLib's {@link emaki.jiuwu.craft.corelib.economy.EconomyManager} selects a backend by id. EmakiStorage
 * publishes an equivalent enum whose {@code providerId()} disagrees with its own provider's {@code id()} in
 * letter case, so reusing that mapping would inherit a backend that can never be resolved. This record keeps
 * the resolved id itself and maps configuration tokens in {@link #fromToken(String, long)}.
 *
 * @param providerId the economy provider id handed to {@code EconomyManager}; empty means "no charge"
 * @param amount     the units charged per batch; zero means "no charge"
 */
public record RecipeCost(String providerId, long amount) {

    /** Provider id for the Vault backend. */
    public static final String VAULT = "Vault";

    /** Provider id for the ExcellentEconomy backend. */
    public static final String EXCELLENT = "ExcellentEconomy";

    /**
     * Creates a cost, normalising the absent case to a single canonical form.
     *
     * @param providerId the provider id; {@code null} becomes an empty string
     * @param amount     the units charged per batch; negatives are clamped to zero
     */
    public RecipeCost {
        providerId = providerId == null ? "" : providerId.trim();
        amount = Math.max(0L, amount);
        if (providerId.isEmpty() || amount == 0L) {
            providerId = "";
            amount = 0L;
        }
    }

    /** {@return a cost that charges nothing} */
    public static RecipeCost none() {
        return new RecipeCost("", 0L);
    }

    /**
     * Resolves a configured currency token into a provider id.
     *
     * <p>Accepted tokens are {@code vault} and {@code excellent}, matching the vocabulary administrators
     * already use in EmakiStorage's {@code unlock_costs.yml}. An unrecognised token yields {@code null} so
     * the caller can record a load issue rather than silently charging the wrong wallet.
     *
     * @param token  the configured token; {@code null} or blank yields {@link #none()}
     * @param amount the units charged per batch
     * @return the resolved cost, or {@code null} when the token is not recognised
     */
    public static RecipeCost fromToken(String token, long amount) {
        if (token == null || token.isBlank()) {
            return none();
        }
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "vault" -> new RecipeCost(VAULT, amount);
            case "excellent", "excellenteconomy" -> new RecipeCost(EXCELLENT, amount);
            default -> null;
        };
    }

    /** {@return whether this cost actually charges anything} */
    public boolean charges() {
        return !providerId.isEmpty() && amount > 0L;
    }

    /**
     * Computes the total charged for a batch.
     *
     * <p>Overflow-safe in the same way as the material side: a batch large enough to overflow reports
     * {@link Long#MAX_VALUE} rather than wrapping into an affordable-looking number.
     *
     * @param batch how many times the recipe is applied; values below 1 are treated as 1
     * @return the charged units, saturating at {@link Long#MAX_VALUE}
     */
    public long totalFor(long batch) {
        if (!charges()) {
            return 0L;
        }
        long safeBatch = Math.max(1L, batch);
        try {
            return Math.multiplyExact(amount, safeBatch);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
