package emaki.jiuwu.craft.skills.trigger;

@FunctionalInterface
public interface TriggerDispatcher {

    void dispatch(TriggerInvocation invocation);
}
