package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.cache.CacheManager;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;

public final class LevelAntiAbuseService {

    private static final int SPAWNER_CACHE_MAX_CHUNKS = 4096;
    private static final long SPAWNER_CACHE_TTL_MILLIS = 5000L;

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    }

    private final Map<String, Long> placedBlocks = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final CacheManager<ChunkKey, List<long[]>> spawnerChunks =
            new CacheManager<>(SPAWNER_CACHE_MAX_CHUNKS, SPAWNER_CACHE_TTL_MILLIS);
    private AppConfig config;

    public LevelAntiAbuseService(AppConfig config) {
        this.config = config;
    }

    public void config(AppConfig config) {
        this.config = config;
    }

    public void recordPlacedBlock(Location location) {
        if (location == null) {
            return;
        }
        invalidateSpawnerChunk(location);
        if (config == null || !config.placedBlockTracking()) {
            return;
        }
        cleanupPlacedBlocks();
        placedBlocks.put(key(location), System.currentTimeMillis());
    }

    public boolean removePlacedBlock(Location location) {
        if (location == null) {
            return false;
        }
        invalidateSpawnerChunk(location);
        cleanupPlacedBlocks();
        return placedBlocks.remove(key(location)) != null;
    }

    public void clearPlacedBlock(Location location) {
        if (location != null) {
            placedBlocks.remove(key(location));
        }
    }

    public boolean isOnCooldown(Player player, SourceRuleConfig source) {
        if (player == null || source == null || source.expCooldownTicks() <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = cooldownKey(player.getUniqueId(), source);
        Long until = cooldowns.get(key);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            cooldowns.remove(key, until);
            return false;
        }
        return true;
    }

    public void markCooldown(Player player, SourceRuleConfig source) {
        if (player == null || source == null || source.expCooldownTicks() <= 0) {
            return;
        }
        cooldowns.put(cooldownKey(player.getUniqueId(), source), System.currentTimeMillis() + source.expCooldownTicks() * 50L);
    }

    public boolean nearSpawner(Location location, int radius) {
        if (location == null || radius < 0) {
            return false;
        }
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int scanRadius = Math.min(16, Math.max(0, radius));
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        int minY = Math.max(world.getMinHeight(), baseY - scanRadius);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + scanRadius);
        int minChunkX = (baseX - scanRadius) >> 4;
        int maxChunkX = (baseX + scanRadius) >> 4;
        int minChunkZ = (baseZ - scanRadius) >> 4;
        int maxChunkZ = (baseZ + scanRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (long[] position : spawnerPositions(world, chunkX, chunkZ)) {
                    int dx = (int) position[0] - baseX;
                    int dz = (int) position[2] - baseZ;
                    int dy = (int) position[1] - baseY;
                    if (dx < -scanRadius || dx > scanRadius
                            || dz < -scanRadius || dz > scanRadius
                            || dy < -scanRadius || dy > scanRadius) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private List<long[]> spawnerPositions(World world, int chunkX, int chunkZ) {
        ChunkKey cacheKey = new ChunkKey(world.getUID(), chunkX, chunkZ);
        List<long[]> cached = spawnerChunks.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        List<long[]> positions = new ArrayList<>();
        for (BlockState state : chunk.getTileEntities()) {
            if (state != null && state.getType() == Material.SPAWNER) {
                positions.add(new long[]{state.getX(), state.getY(), state.getZ()});
            }
        }
        List<long[]> result = List.copyOf(positions);
        spawnerChunks.put(cacheKey, result);
        return result;
    }

    private void invalidateSpawnerChunk(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        spawnerChunks.invalidate(new ChunkKey(world.getUID(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    private void cleanupPlacedBlocks() {
        if (config == null || config.placedBlockRecordTtlTicks() <= 0 || placedBlocks.isEmpty()) {
            return;
        }
        long expireBefore = System.currentTimeMillis() - config.placedBlockRecordTtlTicks() * 50L;
        Iterator<Map.Entry<String, Long>> iterator = placedBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() <= expireBefore) {
                iterator.remove();
            }
        }
    }

    private static String cooldownKey(UUID uuid, SourceRuleConfig source) {
        return uuid + ":" + source.type() + ":" + source.id();
    }

    private static String key(Location location) {
        World world = location.getWorld();
        return (world == null ? "unknown" : world.getUID()) + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
