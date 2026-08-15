package emaki.jiuwu.craft.mobs.spawner;

public sealed interface SpawnRule
        permits NaturalSpawnRule, StructureSpawnRule, PlayerRelativeSpawnRule,
                DayIntervalSpawnRule, CustomSpawnRule, BiomeSpawnRule {

    String mobId();

    String type();
}
