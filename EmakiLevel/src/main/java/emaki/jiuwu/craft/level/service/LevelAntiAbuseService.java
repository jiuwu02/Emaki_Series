package emaki.jiuwu.craft.level.service;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;

public final class LevelAntiAbuseService {

    private final Map<String, Long> placedBlocks = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private AppConfig config;

    public LevelAntiAbuseService(AppConfig config) {
        this.config = config;
    }

    public void config(AppConfig config) {
        this.config = config;
    }

    public void recordPlacedBlock(Location location) {
        if (location == null || config == null || !config.placedBlockTracking()) {
            return;
        }
        cleanupPlacedBlocks();
        placedBlocks.put(key(location), System.currentTimeMillis());
    }

    public boolean removePlacedBlock(Location location) {
        if (location == null) {
            return false;
        }
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
        Long until = cooldowns.get(cooldownKey(player.getUniqueId(), source));
        if (until == null || until <= now) {
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
        for (int x = baseX - scanRadius; x <= baseX + scanRadius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = baseZ - scanRadius; z <= baseZ + scanRadius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.SPAWNER) {
                        return true;
                    }
                }
            }
        }
        return false;
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
