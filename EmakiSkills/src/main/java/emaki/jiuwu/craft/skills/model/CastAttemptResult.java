package emaki.jiuwu.craft.skills.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record CastAttemptResult(boolean success,
        FailureReason failureReason,
        String failureMessage,
        Map<String, ?> replacements) {

    public CastAttemptResult {
        replacements = replacements == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(replacements));
    }

    public CastAttemptResult(boolean success, FailureReason failureReason, String failureMessage) {
        this(success, failureReason, failureMessage, Map.of());
    }

    public static CastAttemptResult ok() {
        return new CastAttemptResult(true, null, null);
    }

    public static CastAttemptResult fail(FailureReason reason, String message) {
        return new CastAttemptResult(false, reason, message);
    }

    public static CastAttemptResult fail(FailureReason reason, String message, Map<String, ?> replacements) {
        return new CastAttemptResult(false, reason, message, replacements);
    }

    public enum FailureReason {
        NOT_IN_CAST_MODE,
        NO_BINDING,
        SKILL_NOT_FOUND,
        SOURCE_LOST,
        FORCED_DELAY_ACTIVE,
        GLOBAL_COOLDOWN_ACTIVE,
        SKILL_COOLDOWN_ACTIVE,
        RESOURCE_INSUFFICIENT,
        MYTHIC_SKILL_NOT_FOUND,
        MYTHIC_CAST_FAILED,
        TRIGGER_DISABLED,
        CANCELLED
    }
}
