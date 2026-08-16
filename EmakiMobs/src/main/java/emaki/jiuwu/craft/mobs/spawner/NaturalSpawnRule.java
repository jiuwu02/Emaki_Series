package emaki.jiuwu.craft.mobs.spawner;

import org.bukkit.block.Biome;

import java.util.Set;

public record NaturalSpawnRule(
        String mobId,
        Set<String> worlds,
        Set<Biome> biomes,
        int yMin,
        int yMax,
        int lightLevelMax,
        double replacementChance,
        int maxNearby,
        CountRange count
) implements SpawnRule {

    @Override
    public String type() {
        return "natural";
    }
}
