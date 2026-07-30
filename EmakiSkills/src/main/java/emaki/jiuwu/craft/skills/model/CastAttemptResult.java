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

    /**
     * Builds a failure that keeps the skill and trigger identity.
     *
     * <p>The other {@code fail} factories blank both ids, which makes a failed cast
     * impossible to attribute afterwards. Use this variant whenever the failing
     * skill is known.
     *
     * @param reason the coarse failure category
     * @param message the message key to render
     * @param replacements placeholders for the message
     * @param skillId the skill that failed, may be {@code null}
     * @param triggerId the trigger that started the cast, may be {@code null}
     * @return a failure result carrying the origin of the failed cast
     */
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
