package emaki.jiuwu.craft.corelib.async;

import java.lang.invoke.MethodHandle;

final class FoliaTaskHandle implements TaskHandle {

    private final Object task;
    private final MethodHandle cancelHandle;
    private final MethodHandle isCancelledHandle;

    FoliaTaskHandle(Object task, MethodHandle cancelHandle, MethodHandle isCancelledHandle) {
        this.task = task;
        this.cancelHandle = cancelHandle;
        this.isCancelledHandle = isCancelledHandle;
    }

    @Override
    public void cancel() {
        if (task == null) {
            return;
        }
        try {
            cancelHandle.invokeExact(task);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to cancel Folia task", throwable);
        }
    }

    @Override
    public boolean isCancelled() {
        if (task == null) {
            return true;
        }
        try {
            return (boolean) isCancelledHandle.invokeExact(task);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to inspect Folia task state", throwable);
        }
    }
}
