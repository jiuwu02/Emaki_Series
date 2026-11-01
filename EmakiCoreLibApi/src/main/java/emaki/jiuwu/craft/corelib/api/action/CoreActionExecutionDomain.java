package emaki.jiuwu.craft.corelib.api.action;

/**
 * Scheduler ownership domains available to third-party CoreLib actions.
 *
 * <p>Declared at registration via {@link CoreActionStage#executionTarget(CoreStagePlanningContext)}
 * or {@link CoreActionSource#executionTarget(CoreStagePlanningContext)}; the declaration decides
 * which thread the stage runs on. It is a tightening of requirements, not a widening: declaring a
 * domain narrower than the Bukkit state you actually touch is safe, declaring it wider throws on
 * Folia at runtime.</p>
 */
public enum CoreActionExecutionDomain {
    /**
     * Not declared. Refused at registration — on Folia an undeclared domain is a crash rather than
     * an error, so this value never reaches a live stage.
     */
    UNDECLARED,

    /**
     * Server global thread. Use for server-wide lookups (all online players), game rules, config
     * writes, and anything that touches no entity- or region-owned state.
     */
    SERVER_GLOBAL,

    /**
     * The current target entity's owner thread. Use for inventory edits, teleport, glowing/silent
     * flags, player messages — anything acting on {@link CoreStageContext#currentTarget()}.
     */
    CONTEXT_ENTITY,

    /**
     * Location/region domain thread. Use for nearby entity searches and for placing blocks or
     * particles at the pipeline origin.
     */
    LOCATION_REGION,

    /**
     * Pure async computation with no Bukkit access in the body. Requires
     * {@link CoreTargetRequirement#NONE}: a stage that both runs async and requires a target is
     * refused at registration as {@code async_needs_target}.
     */
    ASYNC_COMPUTE
}
