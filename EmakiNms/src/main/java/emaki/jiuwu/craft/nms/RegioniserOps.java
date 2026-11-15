package emaki.jiuwu.craft.nms;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class RegioniserOps {

    private static final String REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String TICK_THREAD = "ca.spottedleaf.moonrise.common.util.TickThread";
    private static final String THREAD_REGIONIZER = "io.papermc.paper.threadedregions.ThreadedRegionizer";
    private static final String THREADED_REGION = "io.papermc.paper.threadedregions.ThreadedRegionizer$ThreadedRegion";
    private static final String TICK_REGION_DATA = "io.papermc.paper.threadedregions.TickRegions$TickRegionData";
    private static final String TICK_REGION_SCHEDULER = "io.papermc.paper.threadedregions.TickRegionScheduler";
    private static final String SCHEDULE_HANDLE = "io.papermc.paper.threadedregions.TickRegionScheduler$RegionScheduleHandle";
    private static final String COORDINATE_UTILS = "ca.spottedleaf.moonrise.common.util.CoordinateUtils";
    private static final String LONG_ARRAY_LIST = "it.unimi.dsi.fastutil.longs.LongArrayList";

    private static final Method GLOBAL_TICK_PROBE = NmsAccess.methodOrNull(REGIONIZED_SERVER, "isGlobalTickThread");
    private static final Method GLOBAL_TICK_OWNER = NmsAccess.methodOrNull(REGIONIZED_SERVER, "getInstance");
    private static final Field GLOBAL_TICK_WORLDS = globalTickWorldsField();
    private static final Method SHUTDOWN_PROBE = NmsAccess.methodOrNull(TICK_THREAD, "isShutdownThread");
    private static final Method TICK_THREAD_PROBE = NmsAccess.methodOrNull(TICK_THREAD, "isTickThread");
    private static final Method COMPUTE_ALL_REGIONS = NmsAccess.methodOrNull(THREAD_REGIONIZER, "computeForAllRegions", "java.util.function.Consumer");
    private static final Method REGION_TRY_MARK_TICKING = NmsAccess.methodOrNull(THREADED_REGION, "tryMarkTicking", "java.util.function.BooleanSupplier");
    private static final Method REGION_MARK_NOT_TICKING = NmsAccess.methodOrNull(THREADED_REGION, "markNotTicking");
    private static final Method REGION_OWNED_CHUNKS = NmsAccess.methodOrNull(THREADED_REGION, "getOwnedChunks");
    private static final Method REGION_DATA = NmsAccess.methodOrNull(THREADED_REGION, "getData");
    private static final Method REGION_SCHEDULING_HANDLE = NmsAccess.methodOrNull(TICK_REGION_DATA, "getRegionSchedulingHandle");
    private static final Method REGION_DESCHEDULE = NmsAccess.methodOrNull(TICK_REGION_SCHEDULER, "descheduleRegion", SCHEDULE_HANDLE);
    private static final Method CURRENT_REGION = NmsAccess.methodOrNull(TICK_REGION_SCHEDULER, "getCurrentRegion");
    private static final Method CHUNK_X = NmsAccess.methodOrNull(COORDINATE_UTILS, "getChunkX", "long");
    private static final Method CHUNK_Z = NmsAccess.methodOrNull(COORDINATE_UTILS, "getChunkZ", "long");
    private static final Method LONG_LIST_EMPTY = NmsAccess.methodOrNull(LONG_ARRAY_LIST, "isEmpty");
    private static final Method LONG_LIST_FIRST = NmsAccess.methodOrNull(LONG_ARRAY_LIST, "getLong", "int");

    private RegioniserOps() {
    }

    static boolean isGlobalTickThread() {
        return GLOBAL_TICK_PROBE != null && Boolean.TRUE.equals(NmsAccess.callStatic(GLOBAL_TICK_PROBE));
    }

    static boolean isShutdownThread() {
        return SHUTDOWN_PROBE != null && Boolean.TRUE.equals(NmsAccess.callStatic(SHUTDOWN_PROBE));
    }

    static boolean isTickThread() {
        return TICK_THREAD_PROBE != null && Boolean.TRUE.equals(NmsAccess.callStatic(TICK_THREAD_PROBE));
    }

    static boolean preemptionAvailable() {
        return COMPUTE_ALL_REGIONS != null
                && REGION_TRY_MARK_TICKING != null
                && REGION_MARK_NOT_TICKING != null
                && REGION_OWNED_CHUNKS != null
                && REGION_DATA != null
                && REGION_SCHEDULING_HANDLE != null
                && REGION_DESCHEDULE != null;
    }

    static boolean schedulingAvailable() {
        return COMPUTE_ALL_REGIONS != null
                && REGION_OWNED_CHUNKS != null
                && CURRENT_REGION != null
                && CHUNK_X != null
                && CHUNK_Z != null
                && LONG_LIST_EMPTY != null
                && LONG_LIST_FIRST != null;
    }

    static Object regioniserOf(Object level) {
        Field regioniser = NmsAccess.fieldOrNull(level.getClass(), "regioniser");
        return regioniser == null ? null : NmsAccess.readFieldValue(regioniser, level);
    }

    static void forEachRegion(Object regioniser, Consumer<Object> action) {
        NmsAccess.call(regioniser, COMPUTE_ALL_REGIONS, action);
    }

    static boolean tryMarkTicking(Object region) {
        BooleanSupplier neverTicks = () -> false;
        return Boolean.TRUE.equals(NmsAccess.call(region, REGION_TRY_MARK_TICKING, neverTicks));
    }

    static void markNotTicking(Object region) {
        NmsAccess.call(region, REGION_MARK_NOT_TICKING);
    }

    static boolean hasOwnedChunks(Object region) {
        Object chunks = NmsAccess.call(region, REGION_OWNED_CHUNKS);
        return !Boolean.TRUE.equals(NmsAccess.call(chunks, LONG_LIST_EMPTY));
    }

    static long firstOwnedChunk(Object region) {
        Object chunks = NmsAccess.call(region, REGION_OWNED_CHUNKS);
        return (Long) NmsAccess.call(chunks, LONG_LIST_FIRST, 0);
    }

    static void descheduleRegion(Object region) {
        Object handle = NmsAccess.call(NmsAccess.call(region, REGION_DATA), REGION_SCHEDULING_HANDLE);
        NmsAccess.callStatic(REGION_DESCHEDULE, handle);
    }

    static Object currentRegion() {
        return CURRENT_REGION == null ? null : NmsAccess.callStatic(CURRENT_REGION);
    }

    static void dropFromGlobalWorlds(Object level) {
        if (GLOBAL_TICK_OWNER == null || GLOBAL_TICK_WORLDS == null) {
            return;
        }
        List<?> worlds = (List<?>) NmsAccess.readFieldValue(GLOBAL_TICK_WORLDS, NmsAccess.callStatic(GLOBAL_TICK_OWNER));
        worlds.remove(level);
    }

    static int chunkX(long packedChunk) {
        return (Integer) NmsAccess.callStatic(CHUNK_X, packedChunk);
    }

    static int chunkZ(long packedChunk) {
        return (Integer) NmsAccess.callStatic(CHUNK_Z, packedChunk);
    }

    private static Field globalTickWorldsField() {
        Class<?> owner = NmsAccess.typeOrNull(REGIONIZED_SERVER);
        return owner == null ? null : NmsAccess.fieldOrNull(owner, "worlds");
    }
}
