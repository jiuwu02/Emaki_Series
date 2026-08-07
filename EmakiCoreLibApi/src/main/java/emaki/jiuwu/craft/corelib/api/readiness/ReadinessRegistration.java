package emaki.jiuwu.craft.corelib.api.readiness;

import org.jetbrains.annotations.NotNull;

/**
 * Revocable handle for one readiness callback.
 *
 * <p>Keep it and close it in {@code onDisable}. A handle that outlives its owner keeps a callback
 * pointing into a plugin that is shutting down, and the next time the watched module finishes
 * loading that callback runs against a dead class loader.</p>
 *
 * <p>A handle obtained while the watched module was <em>already</em> ready reports
 * {@link #active()} as {@code false}: the callback ran synchronously inside the registration call
 * and there is nothing left to revoke. Closing it is still safe.</p>
 */
public interface ReadinessRegistration extends AutoCloseable {

    /** {@return whether this handle still holds a pending callback} */
    boolean active();

    /** Removes the pending callback. Idempotent. */
    @Override
    void close();

    /**
     * Creates a handle that holds nothing, used when EmakiCoreLib is absent, the request was
     * rejected, or the callback already ran synchronously.
     *
     * @return an inactive handle
     */
    static @NotNull ReadinessRegistration inactive() {
        return new ReadinessRegistration() {

            @Override
            public boolean active() {
                return false;
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }
}
