package emaki.jiuwu.craft.station.config;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * Typed view of {@code queue_costs.yml}.
 *
 * <p>Tiers are matched against the ordinal of the queue slot being bought, so a batch purchase is priced
 * <em>per slot and summed</em>. Multiplying one unit price by the batch size would let a player lock in the
 * cheapest tier for an arbitrarily large batch.
 *
 * <p>When no tier matches and no fallback exists the purchase is refused rather than made free — fail-closed,
 * because a mispriced file must never hand out queue capacity for nothing.
 *
 * <p>Shape deliberately mirrors EmakiStorage's {@code unlock_costs.yml} so administrators reuse one
 * vocabulary. The currency selector is not shared, though: this module resolves provider ids through
 * {@link emaki.jiuwu.craft.station.recipe.RecipeCost}, whose mapping matches the ids CoreLib's providers
 * actually register under.
 */
public final class QueueCostConfig {

    /**
     * A currency price.
     *
     * <p>{@code amountExpression} is evaluated through CoreLib's expression engine with {@code %count%}
     * bound to the ordinal of the slot being bought.
     *
     * @param providerId       the CoreLib economy provider id
     * @param amount           a literal amount, used when {@code amountExpression} is blank
     * @param amountExpression an expression evaluated per slot, or {@code null}
     */
    public record CurrencyCost(String providerId, double amount, @Nullable String amountExpression) {
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

        /**
         * Tests whether this tier prices a given ordinal.
         *
         * @param count the slot ordinal being bought
         * @return whether the ordinal falls inside this tier
         */
        public boolean covers(int count) {
            return count >= minCount && count <= maxCount;
        }
    }

    /**
     * The per-slot fallback used beyond every tier.
     *
     * @param currency  the currency part, may be {@code null}
     * @param item      the item part, may be {@code null}
     * @param maxAmount mandatory ceiling; an exponential expression loses {@code double} precision at high
     *                  counts, so an uncapped fallback is rejected at load time
     */
    public record Fallback(@Nullable CurrencyCost currency,
            @Nullable ItemCost item,
            double maxAmount) {
    }

    /**
     * Batch purchase options.
     *
     * @param enabled whether the queue page offers a batch purchase click
     * @param options the offered batch sizes
     */
    public record Batch(boolean enabled, List<Integer> options) {

        /**
         * Creates batch options with a defensively copied list.
         *
         * @param enabled whether batch purchase is offered
         * @param options the batch sizes; {@code null} becomes empty
         */
        public Batch(boolean enabled, List<Integer> options) {
            this.enabled = enabled;
            this.options = options == null ? List.of() : List.copyOf(options);
        }

        /** {@return the shipped defaults} */
        public static Batch defaults() {
            return new Batch(true, List.of(1, 5));
        }
    }

    private final List<Tier> tiers;
    private final Fallback fallback;
    private final Batch batch;

    /**
     * Creates the config.
     *
     * @param tiers    the price tiers; {@code null} becomes empty
     * @param fallback the beyond-tiers fallback, may be {@code null}
     * @param batch    the batch options; {@code null} becomes the defaults
     */
    public QueueCostConfig(List<Tier> tiers, @Nullable Fallback fallback, Batch batch) {
        this.tiers = tiers == null ? List.of() : List.copyOf(tiers);
        this.fallback = fallback;
        this.batch = batch == null ? Batch.defaults() : batch;
    }

    /** {@return a config that refuses every purchase, used when the file is missing or broken} */
    public static QueueCostConfig empty() {
        return new QueueCostConfig(List.of(), null, Batch.defaults());
    }

    /** {@return the price tiers in declaration order} */
    public List<Tier> tiers() {
        return tiers;
    }

    /** {@return the beyond-tiers fallback, or {@code null} when none is configured} */
    public @Nullable Fallback fallback() {
        return fallback;
    }

    /** {@return the batch purchase options} */
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
