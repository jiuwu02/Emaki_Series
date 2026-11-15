package emaki.jiuwu.craft.nms.v26_1;

import emaki.jiuwu.craft.nms.NmsAccess;
import emaki.jiuwu.craft.nms.v26_2.FoliaWorldSupport26_2Impl;

import org.bukkit.WorldCreator;

public final class FoliaWorldSupport26_1Impl extends FoliaWorldSupport26_2Impl {

    @Override
    protected Object dimensionKey(WorldCreator creator) {
        return NmsAccess.callStatic(NmsAccess.requireMethod(PAPER_WORLD_LOADER, "dimensionKey", "org.bukkit.NamespacedKey"), creator.key());
    }

    @Override
    protected Object dimensionRegistries() {
        return NmsAccess.call(worldLoaderContext(), NmsAccess.requireMethod(WORLD_LOADER_CONTEXT, "datapackDimensions"));
    }

    @Override
    protected Object worldgenRegistries() {
        return NmsAccess.call(worldLoaderContext(), NmsAccess.requireMethod(WORLD_LOADER_CONTEXT, "datapackWorldgen"));
    }

    @Override
    protected String defaultGeneratorSettings() {
        return "{}";
    }

    @Override
    protected Object levelExecutor() {
        return NmsAccess.readField(console, "executor");
    }

    @Override
    protected Object worldInfo(Object loadedWorldData, Object dimensionKey, long seed, Object primaryLevelData, WorldCreator creator, Object customStem) {
        Object dimensionType = NmsAccess.call(NmsAccess.call(customStem, NmsAccess.requireMethod(LEVEL_STEM, "type")), NmsAccess.requireMethod(HOLDER, "value"));
        Object generator = NmsAccess.call(customStem, NmsAccess.requireMethod(LEVEL_STEM, "generator"));
        Object worldKey = NmsAccess.callStatic(NmsAccess.requireMethod(CRAFT_NAMESPACED_KEY, "fromMinecraft", IDENTIFIER),
                NmsAccess.call(dimensionKey, NmsAccess.requireMethod(RESOURCE_KEY, "identifier")));
        return NmsAccess.construct(CRAFT_WORLD_INFO,
                new String[]{"java.lang.String", "org.bukkit.NamespacedKey", "long", FEATURE_FLAG_SET, "org.bukkit.World$Environment", DIMENSION_TYPE, NMS_CHUNK_GENERATOR, REGISTRY_ACCESS_FROZEN, "java.util.UUID"},
                NmsAccess.call(loadedWorldData, NmsAccess.requireMethod(LOADED_WORLD_DATA, "bukkitName")), worldKey, seed,
                NmsAccess.call(primaryLevelData, NmsAccess.requireMethod(PRIMARY_LEVEL_DATA, "enabledFeatures")), creator.environment(),
                dimensionType, generator, NmsAccess.call(console, NmsAccess.requireMethod(console.getClass(), "registryAccess")),
                NmsAccess.call(loadedWorldData, NmsAccess.requireMethod(LOADED_WORLD_DATA, "uuid")));
    }

    private Object worldLoaderContext() {
        return NmsAccess.readField(console, "worldLoaderContext");
    }
}
