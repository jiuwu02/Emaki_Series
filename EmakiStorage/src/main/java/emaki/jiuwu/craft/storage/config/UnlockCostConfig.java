package emaki.jiuwu.craft.storage.config;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * Typed view of {@code unlock_costs.yml}.
 *
 * <p>Tiers are matched against the ordinal of the slot being bought, so a batch purchase must be
 * priced <em>per slot and summed</em>. Multiplying a single unit price by the batch size would let
 * a player lock in the cheapest tier for an arbitrarily large batch.
 *
 * <p>When no tier matches and no fallback exists the purchase is refused rather than made free —
 * fail-closed, because a mispriced config should never hand out capacity for nothing.
 */
public final class UnlockCostConfig {

    /** Currency backend selector. */
    public enum CurrencyType {
        VAULT,
        EXCELLENT;

        public static CurrencyType fromId(String raw, CurrencyType fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "vault" -> VAULT;
                case "excellent" -> EXCELLENT;
                default -> fallback;
            };
        }

        /** {@return the CoreLib economy provider id for this backend} */
        public String providerId() {
            return this == EXCELLENT ? "excellent" : "vault";
        }
    }

    /**
     * A currency price. {@code amountExpression} is evaluated through CoreLib's expression engine
     * with {@code %count%} bound to the ordinal of the slot being bought.
     *
     * @param type             the currency backend
     * @param currencyId       the backend-specific currency id, blank meaning default
     * @param amount           a literal amount, used when {@code amountExpression} is blank
     * @param amountExpression an expression, evaluated per slot
     */
    public record CurrencyCost(CurrencyType type,
            String currencyId,
            double amount,
            @Nullable String amountExpression) {
    }

    /**
     * An item price.
     *
     * @param sourceToken a CoreLib ItemSource token
     * @param amount      how many units are required
     */
    public record ItemCost(String sourceToken, int amount) {
    }

    /**
     * One price tier.
     *
     * @param minCount the first slot ordinal this tier covers, inclusive
     * @param maxCount the last slot ordinal this tier covers, inclusive
     * @param currency the currency part, may be {@code null}
     * @param item     the item part, may be {@code null}
     */
    public record Tier(int minCount,
            int maxCount,
            @Nullable CurrencyCost currency,
            @Nullable ItemCost item) {

        public boolean covers(int count) {
            return count >= minCount && count <= maxCount;
        }
    }

    /**
     * The per-slot fallback used beyond every tier.
     *
     * @param currency  the currency part, may be {@code null}
     * @param item      the item part, may be {@code null}
     * @param maxAmount mandatory ceiling; an exponential expression overflows {@code double}
     *                  precision at high counts, so an uncapped fallback is rejected at load time
     */
    public record Fallback(@Nullable CurrencyCost currency,
            @Nullable ItemCost item,
            double maxAmount) {
    }

    /**
     * Batch purchase options.
     *
     * @param enabled whether the GUI offers batch buttons
     * @param options the offered batch sizes
     */
    public record Batch(boolean enabled, List<Integer> options) {

        public Batch(boolean enabled, List<Integer> options) {
            this.enabled = enabled;
            this.options = List.copyOf(options);
        }

        public static Batch defaults() {
            return new Batch(true, List.of(1, 5, 10, 64));
        }
    }

    private final List<Tier> tiers;
    private final Fallback fallback;
    private final Batch batch;

    public UnlockCostConfig(List<Tier> tiers, @Nullable Fallback fallback, Batch batch) {
        this.tiers = tiers == null ? List.of() : List.copyOf(tiers);
        this.fallback = fallback;
        this.batch = batch == null ? Batch.defaults() : batch;
    }

    /** {@return a config that refuses every purchase, used when the file is missing or broken} */
    public static UnlockCostConfig empty() {
        return new UnlockCostConfig(List.of(), null, Batch.defaults());
    }

    public List<Tier> tiers() {
        return tiers;
    }

    public @Nullable Fallback fallback() {
        return fallback;
    }

    public Batch batch() {
        return batch;
    }

    /**
     * Finds the tier covering a slot ordinal. Earlier tiers win when ranges overlap.
     *
     * @param count the ordinal of the slot being bought, {@code 1}-based
     * @return the matching tier, or {@code null} when none covers it
     */
    public @Nullable Tier tierFor(int count) {
        for (Tier tier : tiers) {
            if (tier.covers(count)) {
                return tier;
            }
        }
        return null;
    }

    /** {@return whether any pricing rule exists at all} */
    public boolean purchasable() {
        return !tiers.isEmpty() || fallback != null;
    }
}
