package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import org.bukkit.block.Biome;
import org.bukkit.generator.structure.Structure;

import java.util.List;
import java.util.Set;

public record AutonomousSpawnRule(
        String mobId,
        SpawnTrigger trigger,
        long intervalTicks,
        int intervalDays,
        boolean onDayStart,
        String cronExpression,
        Set<String> worlds,
        Set<Biome> biomes,
        List<Structure> structures,
        int yMin,
        int yMax,
        int lightLevelMax,
        String timeOfDay,
        boolean requireSurface,
        DistanceRange distance,
        int maxNearby,
        int maxGlobal,
        CountRange count,
        ConditionBlock condition
) implements SpawnRule {

    public AutonomousSpawnRule {
        if (condition == null) {
            condition = ConditionBlock.empty();
        }
    }

    @Override
    public String type() {
        return "autonomous";
    }
}
