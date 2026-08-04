package emaki.jiuwu.craft.station.material;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;

/**
 * Capability probe results, resolved once at enable time.
 *
 * <p>Capabilities answer one question that neither a version string nor reflection can: is this method
 * actually callable against the build of the providing plugin that is installed right now?
 *
 * <p>Three implementation rules keep the gate working, and breaking any one of them defeats it:
 *
 * <ol>
 *   <li>Capability keys are built with {@link ApiCapability#of(String)} against CoreLib's own class.
 *       Referencing a typed constant published by the providing plugin's API jar would put that class in
 *       EmakiStation's constant pool, and an older provider that lacks it would fail EmakiStation's own
 *       class loading with {@link NoClassDefFoundError} — earlier and harder to guard than a missing
 *       method.</li>
 *   <li>Every guarded call sits inside the {@code if} body that checked its capability. The JVM resolves
 *       a method reference when its {@code invoke} instruction first executes, so a call that never runs
 *       is never resolved. Moving it into a field initialiser, a static block, or an eagerly evaluated
 *       {@code Optional.map} argument would resolve it regardless of the check.</li>
 *   <li>Probing happens at enable time and is cached. A provider's capabilities do not change while the
 *       server runs; a reload re-probes.</li>
 * </ol>
 *
 * @param atomicBatch whether the warehouse can apply a signed batch without routing items through the
 *                    player's inventory
 * @param batchCount  whether the warehouse can count several templates in one round trip
 */
public record StationCapabilities(boolean atomicBatch, boolean batchCount) {

    /** Warehouse capability for inventory-free atomic batches. */
    public static final String ATOMIC_BATCH_KEY = "emakistorage:atomic_batch";

    /** Warehouse capability for multi-template counting. */
    public static final String BATCH_COUNT_KEY = "emakistorage:batch_count";

    /** {@return a probe result with nothing available} */
    public static StationCapabilities none() {
        return new StationCapabilities(false, false);
    }

    /**
     * Probes the installed providers.
     *
     * @return the current capability set
     */
    public static StationCapabilities probe() {
        return new StationCapabilities(
                EmakiCoreLibApi.hasCapability(ApiCapability.of(ATOMIC_BATCH_KEY)),
                EmakiCoreLibApi.hasCapability(ApiCapability.of(BATCH_COUNT_KEY)));
    }

    /**
     * {@return whether the warehouse channel may be enabled at all}
     *
     * <p>Without atomic batches the channel stays disabled rather than degrading to repeated
     * {@code withdrawAsync} calls with compensation. That fallback would flash materials through the
     * player's inventory, leaving a window in which they are visible and, with the right timing,
     * keepable — strictly worse than having no warehouse channel.
     */
    public boolean storageChannelSupported() {
        return atomicBatch;
    }
}
