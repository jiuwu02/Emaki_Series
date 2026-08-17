package emaki.jiuwu.craft.corelib.trigger;

@FunctionalInterface
public interface TriggerDispatcher {

    void dispatch(TriggerInvocation invocation);
}
