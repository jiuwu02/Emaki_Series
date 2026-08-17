package emaki.jiuwu.craft.corelib.cost;

import java.util.List;
import java.util.function.Supplier;

public final class CostReceipt {

    public enum FailureReason {
        SUCCESS,
        PLAYER_UNAVAILABLE,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_MATERIALS,
        COMPENSATION_FAILED
    }

    public record CurrencyRecord(String provider, String currencyId, double amount) {

        public CurrencyRecord {
            provider = provider == null ? "" : provider;
            currencyId = currencyId == null ? "" : currencyId;
            amount = Math.max(0D, amount);
        }
    }

    public record MaterialRecord(List<String> itemTokens, long amount) {

        public MaterialRecord {
            itemTokens = itemTokens == null ? List.of() : List.copyOf(itemTokens);
            amount = Math.max(0L, amount);
        }
    }

    public record RollbackResult(boolean complete,
            List<CurrencyRecord> remainingCurrencies,
            List<MaterialRecord> remainingMaterials) {

        public RollbackResult {
            remainingCurrencies = remainingCurrencies == null ? List.of() : List.copyOf(remainingCurrencies);
            remainingMaterials = remainingMaterials == null ? List.of() : List.copyOf(remainingMaterials);
        }

        public static RollbackResult full() {
            return new RollbackResult(true, List.of(), List.of());
        }
    }

    private final boolean success;
    private final FailureReason failureReason;
    private final boolean compensationComplete;
    private final List<CurrencyRecord> chargedCurrencies;
    private final List<MaterialRecord> chargedMaterials;
    private final List<CurrencyRecord> remainingCurrencies;
    private final List<MaterialRecord> remainingMaterials;
    private final Supplier<RollbackResult> deferredRollback;

    private CostReceipt(boolean success,
            FailureReason failureReason,
            boolean compensationComplete,
            List<CurrencyRecord> chargedCurrencies,
            List<MaterialRecord> chargedMaterials,
            List<CurrencyRecord> remainingCurrencies,
            List<MaterialRecord> remainingMaterials,
            Supplier<RollbackResult> deferredRollback) {
        this.success = success;
        this.failureReason = failureReason;
        this.compensationComplete = compensationComplete;
        this.chargedCurrencies = chargedCurrencies == null ? List.of() : List.copyOf(chargedCurrencies);
        this.chargedMaterials = chargedMaterials == null ? List.of() : List.copyOf(chargedMaterials);
        this.remainingCurrencies = remainingCurrencies == null ? List.of() : List.copyOf(remainingCurrencies);
        this.remainingMaterials = remainingMaterials == null ? List.of() : List.copyOf(remainingMaterials);
        this.deferredRollback = deferredRollback;
    }

    public static CostReceipt noop() {
        return new CostReceipt(true, FailureReason.SUCCESS, true,
                List.of(), List.of(), List.of(), List.of(), null);
    }

    public static CostReceipt success(List<CurrencyRecord> chargedCurrencies,
            List<MaterialRecord> chargedMaterials,
            Supplier<RollbackResult> deferredRollback) {
        return new CostReceipt(true, FailureReason.SUCCESS, true,
                chargedCurrencies, chargedMaterials, List.of(), List.of(), deferredRollback);
    }

    public static CostReceipt failure(FailureReason failureReason, RollbackResult inlineRollback) {
        RollbackResult rb = inlineRollback == null ? RollbackResult.full() : inlineRollback;
        return new CostReceipt(false, failureReason, rb.complete(),
                List.of(), List.of(), rb.remainingCurrencies(), rb.remainingMaterials(), null);
    }

    public static CostReceipt failure(FailureReason failureReason) {
        return failure(failureReason, null);
    }

    public boolean success() { return success; }

    public FailureReason failureReason() { return failureReason; }

    public boolean compensationComplete() { return compensationComplete; }

    public List<CurrencyRecord> chargedCurrencies() { return chargedCurrencies; }

    public List<MaterialRecord> chargedMaterials() { return chargedMaterials; }

    public List<CurrencyRecord> remainingCurrencies() { return remainingCurrencies; }

    public List<MaterialRecord> remainingMaterials() { return remainingMaterials; }

    public RollbackResult rollback() {
        if (deferredRollback == null) {
            return RollbackResult.full();
        }
        return deferredRollback.get();
    }
}
