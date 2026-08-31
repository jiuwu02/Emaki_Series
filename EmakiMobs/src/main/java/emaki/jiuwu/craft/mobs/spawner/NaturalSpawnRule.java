package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
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
        CountRange count,
        ConditionBlock condition
) implements SpawnRule {

    public NaturalSpawnRule {
        if (condition == null) {
            condition = ConditionBlock.empty();
        }
    }

    @Override
    public String type() {
        return "natural";
    }
}
