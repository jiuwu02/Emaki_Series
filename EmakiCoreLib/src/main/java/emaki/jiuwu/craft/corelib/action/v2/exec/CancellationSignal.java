package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.util.concurrent.atomic.AtomicBoolean;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreCancellationToken;

/** Mutable runtime owner of the read-only cancellation token exposed to stages. */
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
