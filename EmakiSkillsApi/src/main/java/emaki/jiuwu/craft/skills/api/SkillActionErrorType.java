package emaki.jiuwu.craft.skills.api;

/** Error category returned by skill-script actions. */
public enum SkillActionErrorType {
    NONE,
    INVALID_ARGUMENT,
    ACTION_NOT_FOUND,
    EXECUTION_EXCEPTION,
    INVALID_STATE,
    PROVIDER_UNAVAILABLE,
    CURRENCY_NOT_FOUND,
    INSUFFICIENT_BALANCE,
    WORLD_NOT_FOUND,
    TEMPLATE_NOT_FOUND,
    SYNTAX_ERROR,
    UNSUPPORTED
}
