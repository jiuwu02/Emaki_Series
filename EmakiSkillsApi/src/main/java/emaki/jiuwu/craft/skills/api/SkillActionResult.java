package emaki.jiuwu.craft.skills.api;

import java.util.Map;

/** Result returned by a skill-script action invocation. */
public record SkillActionResult(boolean success,
        boolean skipped,
        SkillActionErrorType errorType,
        String errorMessage,
        Map<String, Object> data) {

    public static SkillActionResult ok() {
        return new SkillActionResult(true, false, SkillActionErrorType.NONE, null, Map.of());
    }

    public static SkillActionResult ok(Map<String, Object> data) {
        return new SkillActionResult(true, false, SkillActionErrorType.NONE, null, data == null ? Map.of() : Map.copyOf(data));
    }

    public static SkillActionResult skipped(String reason) {
        return new SkillActionResult(true, true, SkillActionErrorType.NONE, reason, Map.of());
    }

    public static SkillActionResult failure(SkillActionErrorType errorType, String errorMessage) {
        return new SkillActionResult(false, false, errorType == null ? SkillActionErrorType.EXECUTION_EXCEPTION : errorType, errorMessage, Map.of());
    }
}
