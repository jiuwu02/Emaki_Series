package emaki.jiuwu.craft.nms;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.world.WorldMountSupport;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class AbstractFoliaWorldSupport implements WorldMountSupport {

    private static final String CRAFT_SERVER = "org.bukkit.craftbukkit.CraftServer";
    private static final String CRAFT_WORLD = "org.bukkit.craftbukkit.CraftWorld";
    private static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";
    private static final String SERVER_LEVEL = "net.minecraft.server.level.ServerLevel";
    private static final String SERVER_CHUNK_CACHE = "net.minecraft.server.level.ServerChunkCache";
    private static final String LEVEL = "net.minecraft.world.level.Level";
    private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
    private static final String LEVEL_STEM = "net.minecraft.world.level.dimension.LevelStem";
    private static final long REGION_ACQUIRE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(15L);

    protected final Object server;
    protected final Object console;

    private final Plugin ownerPlugin;
    private final Method getWorldByName;
    private final Method getWorldByKey;
    private final Method getGeneratorByName;
    private final Method getBiomeProviderByName;
    private final Method getCraftWorldHandle;
    private final Method getAllLevels;
    private final Method addLevel;
    private final Method removeLevel;
    private final Method getLevelByKey;
    private final Method isServerThread;
    private final Method levelDimension;
    private final Method levelPlayers;
    private final Method levelWorldView;
    private final Method saveLevelData;
    private final Method getChunkSource;
    private final Method chunkCacheSave;
    private final Method chunkCacheClose;

    protected AbstractFoliaWorldSupport() {
        this.ownerPlugin = JavaPlugin.getProvidingPlugin(getClass());
        this.server = Bukkit.getServer();
        this.console = NmsAccess.call(server, NmsAccess.requireMethod(CRAFT_SERVER, "getServer"));
        Method worldByName = NmsAccess.requireMethod(CRAFT_SERVER, "getWorld", "java.lang.String");
        Method worldByKey = NmsAccess.methodOrNull(CRAFT_SERVER, "getWorld", "org.bukkit.NamespacedKey");
        if (worldByKey == null) {
            worldByKey = NmsAccess.requireMethod(CRAFT_SERVER, "getWorld", "net.kyori.adventure.key.Key");
        }
        this.getWorldByName = worldByName;
        this.getWorldByKey = worldByKey;
        this.getGeneratorByName = NmsAccess.requireMethod(CRAFT_SERVER, "getGenerator", "java.lang.String");
        this.getBiomeProviderByName = NmsAccess.requireMethod(CRAFT_SERVER, "getBiomeProvider", "java.lang.String");
        this.getCraftWorldHandle = NmsAccess.requireMethod(CRAFT_WORLD, "getHandle");
        this.getAllLevels = NmsAccess.requireMethod(console.getClass(), "getAllLevels");
        this.addLevel = NmsAccess.requireMethod(console.getClass(), "addLevel", SERVER_LEVEL);
        this.removeLevel = NmsAccess.requireMethod(console.getClass(), "removeLevel", SERVER_LEVEL);
        this.getLevelByKey = NmsAccess.requireMethod(console.getClass(), "getLevel", RESOURCE_KEY);
        this.isServerThread = NmsAccess.requireMethod(console.getClass(), "isSameThread");
        this.levelDimension = NmsAccess.requireMethod(SERVER_LEVEL, "dimension");
        this.levelPlayers = NmsAccess.requireMethod(SERVER_LEVEL, "players");
        this.levelWorldView = NmsAccess.requireMethod(SERVER_LEVEL, "getWorld");
        this.saveLevelData = NmsAccess.requireMethod(SERVER_LEVEL, "saveLevelData", "boolean");
        this.getChunkSource = NmsAccess.requireMethod(SERVER_LEVEL, "getChunkSource");
        this.chunkCacheSave = NmsAccess.requireMethod(SERVER_CHUNK_CACHE, "save", "boolean");
        this.chunkCacheClose = NmsAccess.requireMethod(SERVER_CHUNK_CACHE, "close", "boolean");
    }

    @Override
    public World create(WorldCreator creator) {
        return onGlobalThread(() -> createWorldNow(creator));
    }

    @Override
    public boolean unload(World world, boolean save) {
        if (world == null) {
            return false;
        }
        if (RegioniserOps.isShutdownThread()) {
            logger().info("Server is shutting down, leaving '" + world.getName() + "' for the shutdown save.");
            return false;
        }
        return onGlobalThread(() -> unloadWorldNow(world, save));
    }

    @Override
    public void save(World world, Runnable onSaved) {
        Object level = NmsAccess.call(world, getCraftWorldHandle);
        if (RegioniserOps.isShutdownThread()) {
            NmsAccess.call(level, saveLevelData, true);
            onSaved.run();
            return;
        }
        Object regioniser = RegioniserOps.regioniserOf(level);
        if (regioniser == null || !RegioniserOps.schedulingAvailable()) {
            flushWholeWorld(level, onSaved);
            return;
        }
        Object current = RegioniserOps.currentRegion();
        List<Long> targets = new ArrayList<>();
        boolean[] saveCurrent = new boolean[1];
        RegioniserOps.forEachRegion(regioniser, region -> {
            if (region == current) {
                saveCurrent[0] = true;
                return;
            }
            if (RegioniserOps.hasOwnedChunks(region)) {
                targets.add(RegioniserOps.firstOwnedChunk(region));
            }
        });
        if (saveCurrent[0]) {
            NmsAccess.call(NmsAccess.call(level, getChunkSource), chunkCacheSave, true);
        }
        AtomicInteger remaining = new AtomicInteger(targets.size());
        Runnable finish = () -> {
            NmsAccess.call(level, saveLevelData, true);
            onSaved.run();
        };
        if (targets.isEmpty()) {
            finish.run();
            return;
        }
        for (long target : targets) {
            Bukkit.getRegionScheduler().execute(ownerPlugin, world, RegioniserOps.chunkX(target), RegioniserOps.chunkZ(target), () -> {
                try {
                    NmsAccess.call(NmsAccess.call(level, getChunkSource), chunkCacheSave, true);
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        finish.run();
                    }
                }
            });
        }
    }

    protected abstract Object createLevel(WorldCreator creator, ChunkGenerator chunkGenerator, BiomeProvider biomeProvider, Object actualDimension);

    protected abstract void initLevel(Object serverLevel, WorldCreator creator);

    protected void closeStorage(Object serverLevel) {
    }

    private World createWorldNow(WorldCreator creator) {
        if (!hasAnyLevel()) {
            throw new IllegalStateException("Cannot create additional worlds on STARTUP");
        }
        if (creator == null) {
            throw new IllegalArgumentException("WorldCreator cannot be null");
        }
        String name = creator.name();
        ChunkGenerator chunkGenerator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();
        World world = existingWorld(name);
        World worldByKey = existingWorldByKey(creator.key());
        if (world != null || worldByKey != null) {
            if (world == worldByKey) {
                return world;
            }
            throw new IllegalArgumentException("World key " + creator.key() + " and world name " + name + " refer to different existing worlds");
        }
        if (chunkGenerator == null) {
            chunkGenerator = (ChunkGenerator) NmsAccess.call(server, getGeneratorByName, name);
        }
        if (biomeProvider == null) {
            biomeProvider = (BiomeProvider) NmsAccess.call(server, getBiomeProviderByName, name);
        }
        Object serverLevel = createLevel(creator, chunkGenerator, biomeProvider, levelStemKey(creator.environment()));
        if (serverLevel == null || existingWorld(name) != null) {
            return null;
        }
        NmsAccess.call(console, addLevel, serverLevel);
        initLevel(serverLevel, creator);
        return (World) NmsAccess.call(serverLevel, levelWorldView);
    }

    private boolean unloadWorldNow(World world, boolean save) {
        Object level = NmsAccess.call(world, getCraftWorldHandle);
        Object dimensionKey = NmsAccess.call(level, levelDimension);
        if (NmsAccess.call(console, getLevelByKey, dimensionKey) == null) {
            return false;
        }
        if (dimensionKey.equals(NmsAccess.readStaticField(LEVEL, "OVERWORLD"))) {
            return false;
        }
        List<?> players = (List<?>) NmsAccess.call(level, levelPlayers);
        if (!players.isEmpty()) {
            return false;
        }
        if (!new WorldUnloadEvent(world).callEvent()) {
            return false;
        }
        List<Object> acquiredRegions = acquireAllRegions(level);
        if (acquiredRegions == null) {
            logger().log(Level.SEVERE, "Could not take ownership of every ticking region in '" + world.getName() + "' within "
                    + TimeUnit.NANOSECONDS.toSeconds(REGION_ACQUIRE_TIMEOUT_NANOS) + "s, leaving the world loaded.");
            return false;
        }
        RegioniserOps.dropFromGlobalWorlds(level);
        try {
            if (save) {
                NmsAccess.call(level, saveLevelData, true);
            }
            NmsAccess.call(NmsAccess.call(level, getChunkSource), chunkCacheClose, false);
            closeStorage(level);
        } catch (Exception closeFailure) {
            logger().log(Level.SEVERE, "Error while closing the chunk system of '" + world.getName() + "'", closeFailure);
        }
        removeFromCraftServer(world);
        NmsAccess.call(console, removeLevel, level);
        return true;
    }

    private List<Object> acquireAllRegions(Object level) {
        Object regioniser = RegioniserOps.regioniserOf(level);
        if (regioniser == null || !RegioniserOps.preemptionAvailable()) {
            return List.of();
        }
        Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        long deadline = System.nanoTime() + REGION_ACQUIRE_TIMEOUT_NANOS;
        while (true) {
            List<Object> pending = new ArrayList<>();
            RegioniserOps.forEachRegion(regioniser, region -> {
                if (!owned.contains(region)) {
                    pending.add(region);
                }
            });
            if (pending.isEmpty()) {
                return new ArrayList<>(owned);
            }
            for (Object region : pending) {
                if (RegioniserOps.tryMarkTicking(region)) {
                    owned.add(region);
                    RegioniserOps.descheduleRegion(region);
                }
            }
            if (System.nanoTime() >= deadline) {
                for (Object region : owned) {
                    RegioniserOps.markNotTicking(region);
                }
                return null;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
        }
    }

    private void removeFromCraftServer(World world) {
        Map<?, ?> worlds = (Map<?, ?>) NmsAccess.readField(server, "worlds");
        worlds.remove(world.getName().toLowerCase(Locale.ROOT));
    }

    private void flushWholeWorld(Object level, Runnable onSaved) {
        NmsAccess.call(NmsAccess.call(level, getChunkSource), chunkCacheSave, true);
        NmsAccess.call(level, saveLevelData, true);
        onSaved.run();
    }

    private boolean hasAnyLevel() {
        Iterable<?> levels = (Iterable<?>) NmsAccess.call(console, getAllLevels);
        return levels.iterator().hasNext();
    }

    private World existingWorld(String name) {
        return (World) NmsAccess.call(server, getWorldByName, name);
    }

    private World existingWorldByKey(NamespacedKey key) {
        return (World) NmsAccess.call(server, getWorldByKey, key);
    }

    private Object levelStemKey(World.Environment environment) {
        String stemField = switch (environment) {
            case NORMAL -> "OVERWORLD";
            case NETHER -> "NETHER";
            case THE_END -> "END";
            default -> throw new IllegalArgumentException("Unsupported environment " + environment);
        };
        return NmsAccess.readStaticField(LEVEL_STEM, stemField);
    }

    private <T> T onGlobalThread(Supplier<T> action) {
        if (RegioniserOps.isGlobalTickThread() || isServerTickThread()) {
            return action.get();
        }
        if (RegioniserOps.isShutdownThread()) {
            throw new IllegalStateException("Worlds cannot be mounted or unmounted while the server is shutting down");
        }
        if (RegioniserOps.isTickThread()) {
            throw new IllegalStateException("Worlds must be mounted or unmounted from the global thread or an async thread, not from a region tick thread ("
                    + Thread.currentThread().getName() + ")");
        }
        CompletableFuture<T> pending = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(ownerPlugin, () -> {
            try {
                pending.complete(action.get());
            } catch (Throwable failure) {
                pending.completeExceptionally(failure);
            }
        });
        try {
            return pending.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (cause instanceof Error errorFailure) {
                throw errorFailure;
            }
            throw failure;
        }
    }

    private boolean isServerTickThread() {
        return Boolean.TRUE.equals(NmsAccess.call(console, isServerThread));
    }

    private Logger logger() {
        return ownerPlugin.getLogger();
    }
}
