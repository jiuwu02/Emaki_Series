package emaki.jiuwu.craft.corelib.api.readiness;

import org.jetbrains.annotations.NotNull;

/**
 * A standing listener for one module's readiness transitions.
 *
 * <p>This is the counterpart of {@code EmakiCoreLibApi.whenReady}, which fires once and is then
 * dropped. A listener stays registered and is notified on every transition, which is what a consumer
 * caching another module's content needs: the cache has to be invalidated when that module starts
 * reloading and rebuilt when it finishes.</p>
 *
 * <p><strong>Do not re-register from inside the callback.</strong> That is unnecessary here, and with
 * {@code whenReady} it recurses until the stack overflows, because the module is already marked ready
 * by the time callbacks run.</p>
 *
 * <p><strong>Thread:</strong> the callback runs on whichever thread published the transition, which
 * is not guaranteed to be a Bukkit owner thread. Schedule explicitly before touching players,
 * inventories, worlds or GUIs.</p>
 */
@FunctionalInterface
public interface ModuleReadinessListener {

    /**
     * Called when the watched module publishes a readiness transition.
     *
     * <p>An exception thrown here is logged by EmakiCoreLib and does not stop the other listeners
     * from being notified.</p>
     *
     * @param phase what the module just published
     */
    void onReadinessChanged(@NotNull ModuleReadinessPhase phase);
}
