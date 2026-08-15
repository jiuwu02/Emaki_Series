package emaki.jiuwu.craft.skills.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record CastAttemptResult(boolean success,
        FailureReason failureReason,
        String failureMessage,
        Map<String, ?> replacements,
        String skillId,
        String triggerId) {

    public CastAttemptResult {
        replacements = replacements == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(replacements));
        skillId = skillId == null ? "" : skillId;
        triggerId = triggerId == null ? "" : triggerId;
    }

    public static CastAttemptResult ok(String skillId, String triggerId) {
        return new CastAttemptResult(true, null, null, Map.of(), skillId, triggerId);
    }

    public static CastAttemptResult fail(FailureReason reason, String message) {
        return fail(reason, message, Map.of());
    }

    public static CastAttemptResult fail(FailureReason reason, String message, Map<String, ?> replacements) {
        return new CastAttemptResult(false, reason, message, replacements, "", "");
    }

    public static CastAttemptResult fail(FailureReason reason,
            String message,
            Map<String, ?> replacements,
            String skillId,
            String triggerId) {
        return new CastAttemptResult(false, reason, message, replacements, skillId, triggerId);
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
