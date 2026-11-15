package emaki.jiuwu.craft.nms.v26_2;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import emaki.jiuwu.craft.nms.AbstractFoliaWorldSupport;
import emaki.jiuwu.craft.nms.NmsAccess;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

public class FoliaWorldSupport26_2Impl extends AbstractFoliaWorldSupport {

    protected static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";
    protected static final String SERVER_LEVEL = "net.minecraft.server.level.ServerLevel";
    protected static final String PAPER_WORLD_LOADER = "io.papermc.paper.world.PaperWorldLoader";
    protected static final String LOADED_WORLD_DATA = "io.papermc.paper.world.PaperWorldLoader$LoadedWorldData";
    protected static final String PAPER_WORLD_PDC = "io.papermc.paper.world.saveddata.PaperWorldPDC";
    protected static final String PAPER_LEVEL_OVERRIDES = "io.papermc.paper.world.saveddata.PaperLevelOverrides";
    protected static final String WORLD_FOLDER_MIGRATION = "io.papermc.paper.world.migration.WorldFolderMigration";
    protected static final String LEVEL_STORAGE_ACCESS = "net.minecraft.world.level.storage.LevelStorageSource$LevelStorageAccess";
    protected static final String LEVEL_STORAGE_SOURCE = "net.minecraft.world.level.storage.LevelStorageSource";
    protected static final String PRIMARY_LEVEL_DATA = "net.minecraft.world.level.storage.PrimaryLevelData";
    protected static final String LEVEL_RESOURCE = "net.minecraft.world.level.storage.LevelResource";
    protected static final String SAVED_DATA_STORAGE = "net.minecraft.world.level.storage.SavedDataStorage";
    protected static final String SAVED_DATA_TYPE = "net.minecraft.world.level.saveddata.SavedDataType";
    protected static final String REGISTRY_ACCESS = "net.minecraft.core.RegistryAccess";
    protected static final String REGISTRY_ACCESS_FROZEN = "net.minecraft.core.RegistryAccess$Frozen";
    protected static final String REGISTRY = "net.minecraft.core.Registry";
    protected static final String HOLDER = "net.minecraft.core.Holder";
    protected static final String HOLDER_GETTER = "net.minecraft.core.HolderGetter";
    protected static final String HOLDER_LOOKUP_PROVIDER = "net.minecraft.core.HolderLookup$Provider";
    protected static final String REGISTRIES = "net.minecraft.core.registries.Registries";
    protected static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
    protected static final String IDENTIFIER = "net.minecraft.resources.Identifier";
    protected static final String LEVEL_STEM = "net.minecraft.world.level.dimension.LevelStem";
    protected static final String DIMENSION_TYPE = "net.minecraft.world.level.dimension.DimensionType";
    protected static final String WORLD_GEN_SETTINGS = "net.minecraft.world.level.levelgen.WorldGenSettings";
    protected static final String WORLD_OPTIONS = "net.minecraft.world.level.levelgen.WorldOptions";
    protected static final String WORLD_DIMENSIONS = "net.minecraft.world.level.levelgen.WorldDimensions";
    protected static final String WORLD_DIMENSIONS_COMPLETE = "net.minecraft.world.level.levelgen.WorldDimensions$Complete";
    protected static final String PHANTOM_SPAWNER = "net.minecraft.world.level.levelgen.PhantomSpawner";
    protected static final String PATROL_SPAWNER = "net.minecraft.world.level.levelgen.PatrolSpawner";
    protected static final String CAT_SPAWNER = "net.minecraft.world.entity.npc.CatSpawner";
    protected static final String VILLAGE_SIEGE = "net.minecraft.world.entity.ai.village.VillageSiege";
    protected static final String WANDERING_TRADER_SPAWNER = "net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner";
    protected static final String WORLD_DIMENSION_DATA = "net.minecraft.server.dedicated.DedicatedServerProperties$WorldDimensionData";
    protected static final String GSON_HELPER = "net.minecraft.util.GsonHelper";
    protected static final String SERVER_MAIN = "net.minecraft.server.Main";
    protected static final String DATA_FIXERS = "net.minecraft.util.datafix.DataFixers";
    protected static final String DATA_FIXER = "com.mojang.datafixers.DataFixer";
    protected static final String DATA_RESULT = "com.mojang.serialization.DataResult";
    protected static final String DYNAMIC_OPS = "com.mojang.serialization.DynamicOps";
    protected static final String JSON_OPS = "com.mojang.serialization.JsonOps";
    protected static final String CODEC = "com.mojang.serialization.Codec";
    protected static final String BIOME_MANAGER = "net.minecraft.world.level.biome.BiomeManager";
    protected static final String FLAT_LEVEL_GENERATOR_SETTINGS = "net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings";
    protected static final String CRAFT_WORLD_INFO = "org.bukkit.craftbukkit.generator.CraftWorldInfo";
    protected static final String CRAFT_NAMESPACED_KEY = "org.bukkit.craftbukkit.util.CraftNamespacedKey";
    protected static final String NMS_CHUNK_GENERATOR = "net.minecraft.world.level.chunk.ChunkGenerator";
    protected static final String FEATURE_FLAG_SET = "net.minecraft.world.flag.FeatureFlagSet";
    protected static final String OPTION_SET = "joptsimple.OptionSet";
    protected static final String REGISTRY_OPS = "net.minecraft.resources.RegistryOps";
    protected static final String BACKGROUND_EXECUTOR = "net.minecraft.util.Util";
    protected static final String WORLD_LOADER_CONTEXT = "net.minecraft.server.WorldLoader$DataLoadContext";

    private static final String[] SERVER_LEVEL_CONSTRUCTOR = {
            MINECRAFT_SERVER, "java.util.concurrent.Executor", LEVEL_STORAGE_ACCESS, WORLD_GEN_SETTINGS, RESOURCE_KEY, LEVEL_STEM,
            "boolean", "long", "java.util.List", "boolean", RESOURCE_KEY, "org.bukkit.World$Environment",
            "org.bukkit.generator.ChunkGenerator", "org.bukkit.generator.BiomeProvider", SAVED_DATA_STORAGE, LOADED_WORLD_DATA};

    private final Method registryLookup;
    private final Method registryValue;
    private final Method registryAccess;
    private final Method worldData;
    private final Method fixerUpper;
    private final Method optionSetHas;
    private final Method migrateWorldFolder;
    private final Method loadWorldData;
    private final Method readExistingSavedData;
    private final Method dataResultValue;
    private final Method worldOptionsSeed;
    private final Method parseGeneratorSettings;
    private final Method dimensionDataCreate;
    private final Method bakeWorldDimensions;
    private final Method dimensionFromSettings;
    private final Method bakedDimensions;
    private final Method bakedRegistryAccess;
    private final Method worldGenSettingsOptions;
    private final Method worldGenSettingsDimensions;
    private final Method levelOverrides;
    private final Method setHardcore;
    private final Method bukkitName;
    private final Method worldUuid;
    private final Method worldPdc;
    private final Method forceWorldUpgrade;
    private final Method dataFixer;
    private final Method obfuscateSeed;
    private final Method stemType;
    private final Method stemGenerator;
    private final Method holderValue;
    private final Method savedDataTypeEntry;
    private final Method dimensionPath;
    private final Method dataResourceId;
    private final Method debugWorld;
    private final Method enabledFeatures;
    private final Method initWorld;
    private final Method setSpawnSettings;
    private final Method prepareLevel;

    public FoliaWorldSupport26_2Impl() {
        super();
        this.registryLookup = NmsAccess.requireMethod(REGISTRY_ACCESS, "lookupOrThrow", RESOURCE_KEY);
        this.registryValue = NmsAccess.requireMethod(REGISTRY, "getValue", RESOURCE_KEY);
        this.registryAccess = NmsAccess.requireMethod(console.getClass(), "registryAccess");
        this.worldData = NmsAccess.requireMethod(console.getClass(), "getWorldData");
        this.fixerUpper = NmsAccess.requireMethod(console.getClass(), "getFixerUpper");
        this.optionSetHas = NmsAccess.requireMethod(OPTION_SET, "has", "java.lang.String");
        this.migrateWorldFolder = NmsAccess.requireMethod(WORLD_FOLDER_MIGRATION, "migrateApiWorld", LEVEL_STORAGE_ACCESS, HOLDER_LOOKUP_PROVIDER, "java.lang.String", RESOURCE_KEY, RESOURCE_KEY);
        this.loadWorldData = NmsAccess.requireMethod(PAPER_WORLD_LOADER, "loadWorldData", MINECRAFT_SERVER, RESOURCE_KEY, "java.lang.String");
        this.readExistingSavedData = NmsAccess.requireMethod(LEVEL_STORAGE_SOURCE, "readExistingSavedData", LEVEL_STORAGE_ACCESS, RESOURCE_KEY, HOLDER_LOOKUP_PROVIDER, SAVED_DATA_TYPE);
        this.dataResultValue = NmsAccess.requireMethod(DATA_RESULT, "result");
        this.worldOptionsSeed = NmsAccess.requireMethod(WORLD_OPTIONS, "seed");
        this.parseGeneratorSettings = NmsAccess.requireMethod(GSON_HELPER, "parse", "java.lang.String");
        this.dimensionDataCreate = NmsAccess.requireMethod(WORLD_DIMENSION_DATA, "create", HOLDER_LOOKUP_PROVIDER);
        this.bakeWorldDimensions = NmsAccess.requireMethod(WORLD_DIMENSIONS, "bake", REGISTRY);
        this.dimensionFromSettings = NmsAccess.requireMethod(WORLD_DIMENSIONS, "get", RESOURCE_KEY);
        this.bakedDimensions = NmsAccess.requireMethod(WORLD_DIMENSIONS_COMPLETE, "dimensions");
        this.bakedRegistryAccess = NmsAccess.requireMethod(WORLD_DIMENSIONS_COMPLETE, "dimensionsRegistryAccess");
        this.worldGenSettingsOptions = NmsAccess.requireMethod(WORLD_GEN_SETTINGS, "options");
        this.worldGenSettingsDimensions = NmsAccess.requireMethod(WORLD_GEN_SETTINGS, "dimensions");
        this.levelOverrides = NmsAccess.requireMethod(LOADED_WORLD_DATA, "levelOverrides");
        this.setHardcore = NmsAccess.requireMethod(PAPER_LEVEL_OVERRIDES, "setHardcore", "boolean");
        this.bukkitName = NmsAccess.requireMethod(LOADED_WORLD_DATA, "bukkitName");
        this.worldUuid = NmsAccess.requireMethod(LOADED_WORLD_DATA, "uuid");
        this.worldPdc = NmsAccess.requireMethod(LOADED_WORLD_DATA, "pdc");
        this.forceWorldUpgrade = NmsAccess.requireMethod(SERVER_MAIN, "forceUpgrade", LEVEL_STORAGE_ACCESS, DATA_FIXER, "boolean", "java.util.function.BooleanSupplier", REGISTRY_ACCESS, "boolean");
        this.dataFixer = NmsAccess.requireMethod(DATA_FIXERS, "getDataFixer");
        this.obfuscateSeed = NmsAccess.requireMethod(BIOME_MANAGER, "obfuscateSeed", "long");
        this.stemType = NmsAccess.requireMethod(LEVEL_STEM, "type");
        this.stemGenerator = NmsAccess.requireMethod(LEVEL_STEM, "generator");
        this.holderValue = NmsAccess.requireMethod(HOLDER, "value");
        this.savedDataTypeEntry = NmsAccess.requireMethod(SAVED_DATA_STORAGE, "set", SAVED_DATA_TYPE, "net.minecraft.world.level.saveddata.SavedData");
        this.dimensionPath = NmsAccess.requireMethod(LEVEL_STORAGE_ACCESS, "getDimensionPath", RESOURCE_KEY);
        this.dataResourceId = NmsAccess.requireMethod(LEVEL_RESOURCE, "id");
        this.debugWorld = NmsAccess.requireMethod(PRIMARY_LEVEL_DATA, "isDebugWorld");
        this.enabledFeatures = NmsAccess.requireMethod(PRIMARY_LEVEL_DATA, "enabledFeatures");
        this.initWorld = NmsAccess.requireMethod(console.getClass(), "initWorld", SERVER_LEVEL, "org.bukkit.WorldCreator");
        this.setSpawnSettings = NmsAccess.requireMethod(SERVER_LEVEL, "setSpawnSettings", "boolean");
        this.prepareLevel = NmsAccess.requireMethod(console.getClass(), "prepareLevel", SERVER_LEVEL);
    }

    @Override
    protected Object createLevel(WorldCreator creator, ChunkGenerator chunkGenerator, BiomeProvider biomeProvider, Object actualDimension) {
        String name = creator.name();
        Object dimensionKey = dimensionKey(creator);
        Object dimensionRegistries = dimensionRegistries();
        Object levelStemRegistry = NmsAccess.call(dimensionRegistries, registryLookup, levelStemRegistryKey());
        Object storageAccess = NmsAccess.readField(console, "storageSource");
        if (NmsAccess.call(NmsAccess.call(NmsAccess.call(console, registryAccess), registryLookup, levelStemRegistryKey()), registryValue, actualDimension) == null) {
            throw new IllegalStateException("Missing configured level stem " + actualDimension);
        }
        try {
            NmsAccess.callStatic(migrateWorldFolder, storageAccess, NmsAccess.call(console, registryAccess), name, actualDimension, dimensionKey);
        } catch (IllegalStateException migrationFailure) {
            throw new RuntimeException("Failed to migrate legacy world " + name, migrationFailure);
        }
        Object loadedWorldData = NmsAccess.callStatic(loadWorldData, console, dimensionKey, name);
        Object primaryLevelData = NmsAccess.call(console, worldData);
        Object storedGenSettings = NmsAccess.callStatic(readExistingSavedData, storageAccess, dimensionKey, NmsAccess.call(console, registryAccess), NmsAccess.readStaticField(WORLD_GEN_SETTINGS, "TYPE"));
        Object worldGenSettings = ((Optional<?>) NmsAccess.call(storedGenSettings, dataResultValue)).orElse(null);
        Object frozen = dimensionRegistries;
        if (worldGenSettings == null) {
            Object worldOptions = NmsAccess.construct(WORLD_OPTIONS, new String[]{"long", "boolean", "boolean"}, creator.seed(), creator.generateStructures(), creator.bonusChest());
            String generatorSettings = creator.generatorSettings().isEmpty() ? defaultGeneratorSettings() : creator.generatorSettings();
            Object dimensionData = NmsAccess.construct(WORLD_DIMENSION_DATA, new String[]{"com.google.gson.JsonObject", "java.lang.String"},
                    NmsAccess.callStatic(parseGeneratorSettings, generatorSettings), creator.type().name().toLowerCase(Locale.ROOT));
            Object worldDimensions = NmsAccess.call(dimensionData, dimensionDataCreate, worldgenRegistries());
            Object complete = NmsAccess.call(worldDimensions, bakeWorldDimensions, levelStemRegistry);
            if (NmsAccess.call(NmsAccess.call(complete, bakedDimensions), registryValue, actualDimension) == null) {
                throw new IllegalStateException("Missing generated level stem " + actualDimension + " for world " + name);
            }
            worldGenSettings = NmsAccess.construct(WORLD_GEN_SETTINGS, new String[]{WORLD_OPTIONS, WORLD_DIMENSIONS}, worldOptions, worldDimensions);
            frozen = NmsAccess.call(complete, bakedRegistryAccess);
            NmsAccess.call(NmsAccess.call(loadedWorldData, levelOverrides), setHardcore, creator.hardcore());
            loadedWorldData = NmsAccess.construct(LOADED_WORLD_DATA, new String[]{"java.lang.String", "java.util.UUID", PAPER_WORLD_PDC, PAPER_LEVEL_OVERRIDES},
                    NmsAccess.call(loadedWorldData, bukkitName), NmsAccess.call(loadedWorldData, worldUuid), NmsAccess.call(loadedWorldData, worldPdc), NmsAccess.call(loadedWorldData, levelOverrides));
        }
        levelStemRegistry = NmsAccess.call(frozen, registryLookup, levelStemRegistryKey());
        Object serverOptions = NmsAccess.readField(console, "options");
        if (Boolean.TRUE.equals(NmsAccess.call(serverOptions, optionSetHas, "forceUpgrade"))) {
            NmsAccess.callStatic(forceWorldUpgrade, storageAccess, NmsAccess.callStatic(dataFixer),
                    NmsAccess.call(serverOptions, optionSetHas, "eraseCache"), (BooleanSupplier) () -> true,
                    frozen, NmsAccess.call(serverOptions, optionSetHas, "recreateRegionFiles"));
        }
        long worldSeed = (Long) NmsAccess.call(NmsAccess.call(worldGenSettings, worldGenSettingsOptions), worldOptionsSeed);
        long biomeZoomSeed = (Long) NmsAccess.callStatic(obfuscateSeed, worldSeed);
        Object customStem = ((Optional<?>) NmsAccess.call(NmsAccess.call(worldGenSettings, worldGenSettingsDimensions), dimensionFromSettings, actualDimension)).orElse(null);
        if (customStem == null) {
            customStem = NmsAccess.call(levelStemRegistry, registryValue, actualDimension);
        }
        if (customStem == null) {
            throw new IllegalStateException("Missing level stem for world " + name + " using key " + actualDimension);
        }
        Object worldInfo = worldInfo(loadedWorldData, dimensionKey, worldSeed, primaryLevelData, creator, customStem);
        if (biomeProvider == null && chunkGenerator != null) {
            biomeProvider = chunkGenerator.getDefaultBiomeProvider((WorldInfo) worldInfo);
        }
        Path worldDataFolder = ((Path) NmsAccess.call(storageAccess, dimensionPath, dimensionKey))
                .resolve((String) NmsAccess.call(NmsAccess.readStaticField(LEVEL_RESOURCE, "DATA"), dataResourceId));
        Object savedDataStorage = NmsAccess.construct(SAVED_DATA_STORAGE, new String[]{"java.nio.file.Path", DATA_FIXER, HOLDER_LOOKUP_PROVIDER},
                worldDataFolder, NmsAccess.callStatic(dataFixer), NmsAccess.call(console, registryAccess));
        NmsAccess.call(savedDataStorage, savedDataTypeEntry, NmsAccess.readStaticField(WORLD_GEN_SETTINGS, "TYPE"),
                NmsAccess.construct(WORLD_GEN_SETTINGS, new String[]{WORLD_OPTIONS, WORLD_DIMENSIONS},
                        NmsAccess.call(worldGenSettings, worldGenSettingsOptions), NmsAccess.call(worldGenSettings, worldGenSettingsDimensions)));
        List<Object> spawners = List.of(
                NmsAccess.construct(PHANTOM_SPAWNER, new String[0]),
                NmsAccess.construct(PATROL_SPAWNER, new String[0]),
                NmsAccess.construct(CAT_SPAWNER, new String[0]),
                NmsAccess.construct(VILLAGE_SIEGE, new String[0]),
                NmsAccess.construct(WANDERING_TRADER_SPAWNER, new String[]{SAVED_DATA_STORAGE}, savedDataStorage));
        List<?> dimensionSpawners = creator.environment() == World.Environment.NORMAL ? spawners : List.of();
        return NmsAccess.construct(SERVER_LEVEL, SERVER_LEVEL_CONSTRUCTOR,
                console, levelExecutor(), storageAccess, worldGenSettings, dimensionKey, customStem,
                NmsAccess.call(primaryLevelData, debugWorld), biomeZoomSeed,
                dimensionSpawners, true, actualDimension,
                creator.environment(), chunkGenerator, biomeProvider, savedDataStorage, loadedWorldData);
    }

    @Override
    protected void initLevel(Object serverLevel, WorldCreator creator) {
        NmsAccess.call(console, initWorld, serverLevel, creator);
        NmsAccess.call(serverLevel, setSpawnSettings, true);
        NmsAccess.call(console, prepareLevel, serverLevel);
    }

    private Object levelStemRegistryKey() {
        return NmsAccess.readStaticField(REGISTRIES, "LEVEL_STEM");
    }

    protected Object dimensionKey(WorldCreator creator) {
        return NmsAccess.callStatic(NmsAccess.requireMethod(CRAFT_NAMESPACED_KEY, "toResourceKey", RESOURCE_KEY, "org.bukkit.NamespacedKey"),
                NmsAccess.readStaticField(REGISTRIES, "DIMENSION"), creator.key());
    }

    protected Object dimensionRegistries() {
        return NmsAccess.call(console, registryAccess);
    }

    protected Object worldgenRegistries() {
        return NmsAccess.call(console, registryAccess);
    }

    protected String defaultGeneratorSettings() {
        Object registries = NmsAccess.call(console, registryAccess);
        Object serializationContext = NmsAccess.call(registries,
                NmsAccess.requireMethod(REGISTRY_ACCESS, "createSerializationContext", DYNAMIC_OPS),
                NmsAccess.readStaticField(JSON_OPS, "INSTANCE"));
        Object flatDefaults = NmsAccess.callStatic(NmsAccess.requireMethod(FLAT_LEVEL_GENERATOR_SETTINGS, "getDefault", HOLDER_GETTER, HOLDER_GETTER, HOLDER_GETTER),
                NmsAccess.call(registries, registryLookup, NmsAccess.readStaticField(REGISTRIES, "BIOME")),
                NmsAccess.call(registries, registryLookup, NmsAccess.readStaticField(REGISTRIES, "STRUCTURE_SET")),
                NmsAccess.call(registries, registryLookup, NmsAccess.readStaticField(REGISTRIES, "PLACED_FEATURE")));
        Object encoded = NmsAccess.call(NmsAccess.readStaticField(FLAT_LEVEL_GENERATOR_SETTINGS, "CODEC"),
                NmsAccess.requireMethod(CODEC, "encodeStart", DYNAMIC_OPS, "java.lang.Object"),
                serializationContext, flatDefaults);
        return NmsAccess.call(encoded, NmsAccess.requireMethod(DATA_RESULT, "getOrThrow")).toString();
    }

    protected Object levelExecutor() {
        return NmsAccess.callStatic(NmsAccess.requireMethod(BACKGROUND_EXECUTOR, "backgroundExecutor"));
    }

    protected Object worldInfo(Object loadedWorldData, Object dimensionKey, long seed, Object primaryLevelData, WorldCreator creator, Object customStem) {
        Object dimensionType = NmsAccess.call(NmsAccess.call(customStem, stemType), holderValue);
        Object generator = NmsAccess.call(customStem, stemGenerator);
        Object worldKey = NmsAccess.callStatic(NmsAccess.requireMethod(CRAFT_NAMESPACED_KEY, "fromMinecraft", IDENTIFIER),
                NmsAccess.call(dimensionKey, NmsAccess.requireMethod(RESOURCE_KEY, "identifier")));
        return NmsAccess.construct(CRAFT_WORLD_INFO,
                new String[]{"java.lang.String", "org.bukkit.NamespacedKey", "long", FEATURE_FLAG_SET, "org.bukkit.World$Environment", DIMENSION_TYPE, NMS_CHUNK_GENERATOR, REGISTRY_ACCESS, "java.util.UUID"},
                NmsAccess.call(loadedWorldData, bukkitName), worldKey, seed,
                NmsAccess.call(primaryLevelData, enabledFeatures), creator.environment(),
                dimensionType, generator, NmsAccess.call(console, registryAccess), NmsAccess.call(loadedWorldData, worldUuid));
    }
}
