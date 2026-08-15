package emaki.jiuwu.craft.mobs.spawner;

import org.bukkit.block.Biome;

import java.util.Set;

public record CustomSpawnRule(
        String mobId,
        long intervalTicks,
        DistanceRange distance,
        Set<Biome> biomes,
        CountRange count,
        int maxNearby
) implements SpawnRule {

    @Override
    public String type() {
        return "custom";
    }
}
