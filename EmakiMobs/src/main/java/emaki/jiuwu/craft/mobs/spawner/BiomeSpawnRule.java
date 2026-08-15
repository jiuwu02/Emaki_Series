package emaki.jiuwu.craft.mobs.spawner;

import org.bukkit.block.Biome;

import java.util.Set;

public record BiomeSpawnRule(
        String mobId,
        Set<Biome> biomes,
        long intervalTicks,
        DistanceRange distance,
        SpawnConditions conditions,
        CountRange count,
        int maxNearby
) implements SpawnRule {

    @Override
    public String type() {
        return "biome";
    }
}
