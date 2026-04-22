package emaki.jiuwu.craft.skills.trigger;

/**
 * Receives {@link TriggerInvocation}s from trigger sources and routes them
 * to the appropriate skill execution pipeline.
 */
@FunctionalInterface
public interface TriggerDispatcher {

    /**
     * Dispatch a trigger invocation for processing.
     *
     * @param invocation the invocation to dispatch
     */
    void dispatch(TriggerInvocation invocation);
}
