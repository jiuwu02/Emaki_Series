package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.concurrent.atomic.AtomicBoolean;

import emaki.jiuwu.craft.corelib.api.action.CoreCancellationToken;

final class CancellationSignal implements CoreCancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    @Override
    public boolean cancelled() {
        return cancelled.get();
    }

    boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }
}
