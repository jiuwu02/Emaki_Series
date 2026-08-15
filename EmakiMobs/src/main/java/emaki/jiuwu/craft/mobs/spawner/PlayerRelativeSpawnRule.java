package emaki.jiuwu.craft.mobs.spawner;

public record PlayerRelativeSpawnRule(
        String mobId,
        DistanceRange distance,
        boolean requireSkyAccess,
        int maxGlobal,
        long intervalTicks,
        CountRange count
) implements SpawnRule {

    @Override
    public String type() {
        return "player_relative";
    }
}
