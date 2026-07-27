package emaki.jiuwu.craft.corelib.execution;

public interface TaskHandle {

    void cancel();

    boolean isCancelled();
}
