package emaki.jiuwu.craft.corelib.async;

public interface TaskHandle {

    void cancel();

    boolean isCancelled();
}
