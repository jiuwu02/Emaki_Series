package emaki.jiuwu.craft.station.material;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;

public record StationCapabilities(boolean atomicBatch, boolean batchCount, boolean reservation) {

    public static final String ATOMIC_BATCH_KEY = "emakistorage:atomic_batch";

    public static final String BATCH_COUNT_KEY = "emakistorage:batch_count";

    public static final String RESERVATION_KEY = "emakistorage:reservation";

    public static StationCapabilities none() {
        return new StationCapabilities(false, false, false);
    }

    public static StationCapabilities probe() {
        return new StationCapabilities(
                EmakiCoreLibApi.hasCapability(ApiCapability.of(ATOMIC_BATCH_KEY)),
                EmakiCoreLibApi.hasCapability(ApiCapability.of(BATCH_COUNT_KEY)),
                EmakiCoreLibApi.hasCapability(ApiCapability.of(RESERVATION_KEY)));
    }

    public boolean storageChannelSupported() {
        return atomicBatch;
    }
}
