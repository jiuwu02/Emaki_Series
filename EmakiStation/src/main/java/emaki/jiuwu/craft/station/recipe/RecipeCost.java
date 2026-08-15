package emaki.jiuwu.craft.station.recipe;

import java.util.Locale;

public record RecipeCost(String providerId, long amount) {

    public static final String VAULT = "Vault";

    public static final String EXCELLENT = "ExcellentEconomy";

    public RecipeCost {
        providerId = providerId == null ? "" : providerId.trim();
        amount = Math.max(0L, amount);
        if (providerId.isEmpty() || amount == 0L) {
            providerId = "";
            amount = 0L;
        }
    }

    public static RecipeCost none() {
        return new RecipeCost("", 0L);
    }

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

    public boolean charges() {
        return !providerId.isEmpty() && amount > 0L;
    }

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
