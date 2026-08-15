package emaki.jiuwu.craft.storage.config;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

public final class UnlockCostConfig {

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

        public String providerId() {
            return this == EXCELLENT ? "excellent" : "vault";
        }
    }

    public record CurrencyCost(CurrencyType type,
            String currencyId,
            double amount,
            @Nullable String amountExpression) {
    }

    public record ItemCost(String sourceToken, int amount) {
    }

    public record Tier(int minCount,
            int maxCount,
            @Nullable CurrencyCost currency,
            @Nullable ItemCost item) {

        public boolean covers(int count) {
            return count >= minCount && count <= maxCount;
        }
    }

    public record Fallback(@Nullable CurrencyCost currency,
            @Nullable ItemCost item,
            double maxAmount) {
    }

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

    public @Nullable Tier tierFor(int count) {
        for (Tier tier : tiers) {
            if (tier.covers(count)) {
                return tier;
            }
        }
        return null;
    }

    public boolean purchasable() {
        return !tiers.isEmpty() || fallback != null;
    }
}
