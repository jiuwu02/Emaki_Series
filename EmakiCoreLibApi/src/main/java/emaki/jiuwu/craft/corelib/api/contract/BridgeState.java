package emaki.jiuwu.craft.corelib.api.contract;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the bridge installed for a static API facade.
 *
 * <p>Install and uninstall are lifecycle operations; accessors read the current
 * bridge through {@link #get()} at the point of use.
 *
 * @param <T> the bridge type
 */
public final class BridgeState<T> {

    private final AtomicReference<T> value = new AtomicReference<>();

    /**
     * Activates the given bridge as the current instance.
     *
     * @param bridge the bridge to activate
     */
    public void activate(T bridge) {
        value.set(bridge);
    }

    /**
     * Returns the active bridge, or {@code null} when none is installed.
     *
     * @return the active bridge or {@code null}
     */
    public T get() {
        return value.get();
    }

    /**
     * Deactivates the active bridge only when it is still the given instance.
     *
     * @param bridge the bridge expected to be active
     */
    public void deactivate(T bridge) {
        value.compareAndSet(bridge, null);
    }
}
