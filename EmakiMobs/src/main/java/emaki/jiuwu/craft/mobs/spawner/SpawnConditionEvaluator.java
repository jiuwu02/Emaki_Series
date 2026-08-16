package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.generator.structure.Structure;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnConditionEvaluator {

    private final MobIdentifier mobIdentifier;

    public SpawnConditionEvaluator(MobIdentifier mobIdentifier) {
        this.mobIdentifier = mobIdentifier;
    }

    public boolean matchesNatural(Location location, NaturalSpawnRule rule) {
        if (!rule.worlds().isEmpty()) {
            World world = location.getWorld();
            if (world == null || !rule.worlds().contains(world.getName())) return false;
        }
        if (!matchesBiomes(location, rule.biomes())) return false;
        int y = location.getBlockY();
        if (y < rule.yMin() || y > rule.yMax()) return false;
        if (location.getBlock().getLightLevel() > rule.lightLevelMax()) return false;
        if (rule.maxNearby() > 0 && countNearby(location, rule.mobId(), 64) >= rule.maxNearby()) return false;
        return true;
    }

    public boolean matchesBiomes(Location location, Set<Biome> biomes) {
        if (biomes.isEmpty()) return true;
        return biomes.contains(location.getBlock().getBiome());
    }

    public boolean matchesWorlds(Location location, Set<String> worlds) {
        if (worlds.isEmpty()) return true;
        World world = location.getWorld();
        return world != null && worlds.contains(world.getName());
    }

    public boolean matchesTimeOfDay(World world, String timeOfDay) {
        if (timeOfDay == null || "any".equalsIgnoreCase(timeOfDay)) return true;
        long time = world.getTime();
        return switch (timeOfDay.toLowerCase()) {
            case "day"   -> time >= 1000 && time < 13000;
            case "night" -> time >= 13000 || time < 1000;
            default      -> true;
        };
    }

    public boolean isInStructures(Location location, List<Structure> structures) {
        Chunk chunk = location.getChunk();
        for (var gen : chunk.getStructures()) {
            if (structures.contains(gen.getStructure())
                    && gen.getBoundingBox().contains(
                            location.getX(), location.getY(), location.getZ())) {
                return true;
            }
        }
        return false;
    }

    public long countNearby(Location location, String mobId, int radius) {
        World world = location.getWorld();
        if (world == null) return 0;
        return world.getNearbyEntities(location, radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity le && mobId.equals(mobIdentifier.readId(le)))
                .count();
    }

    public long countGlobal(String mobId) {
        return Bukkit.getWorlds().stream()
                .flatMap(w -> w.getLivingEntities().stream())
                .filter(e -> mobId.equals(mobIdentifier.readId(e)))
                .count();
    }

    public Location findSurface(Location center, int minDist, int maxDist) {
        World world = center.getWorld();
        if (world == null) return null;
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        int dist = minDist >= maxDist ? minDist : ThreadLocalRandom.current().nextInt(minDist, maxDist + 1);
        int x = (int) (center.getX() + Math.cos(angle) * dist);
        int z = (int) (center.getZ() + Math.sin(angle) * dist);
        int y = world.getHighestBlockYAt(x, z);
        Location candidate = new Location(world, x + 0.5, y + 1.0, z + 0.5);
        Block above = candidate.getBlock();
        Block ground = above.getRelative(0, -1, 0);
        if (!above.isPassable() || !ground.getType().isSolid()) return null;
        return candidate;
    }
}
