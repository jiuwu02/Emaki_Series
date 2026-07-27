package emaki.jiuwu.craft.corelib.api.action;

/**
 * Scheduler ownership domains available to third-party CoreLib actions.
 */
public enum CoreActionExecutionDomain {
    UNDECLARED,
    SERVER_GLOBAL,
    CONTEXT_ENTITY,
    LOCATION_REGION,
    ASYNC_COMPUTE
}
