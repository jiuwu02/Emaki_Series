package emaki.jiuwu.craft.mobs.spawner;

public sealed interface SpawnRule permits NaturalSpawnRule, AutonomousSpawnRule {

    String mobId();

    String type();
}
