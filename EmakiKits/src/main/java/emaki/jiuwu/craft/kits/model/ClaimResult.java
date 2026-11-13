package emaki.jiuwu.craft.kits.model;
import java.util.List;

public record ClaimResult(Status status, String reasonKey, String kitId, String periodKey,
                          List<String> auditIds) {

    public enum Status {
        SUCCESS,
        ALREADY_CLAIMED,
        CONDITION_FAILED,
        LIMIT_EXCEEDED,
        PERMISSION_DENIED,
        CONFIG_ERROR,
        PAYMENT_FAILED,
        INVALID_SELECTION,
        CDK_EXHAUSTED,
        STORAGE_FAILED
    }

    public static ClaimResult success(String kitId, String periodKey, List<String> auditIds) {
        return new ClaimResult(Status.SUCCESS, "success", kitId, periodKey, List.copyOf(auditIds));
    }

    public static ClaimResult failure(Status status, String reasonKey, String kitId, String periodKey) {
        return new ClaimResult(status, reasonKey, kitId, periodKey, List.of());
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }
}
