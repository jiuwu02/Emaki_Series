package emaki.jiuwu.craft.mobs.spawner;

public record DayIntervalSpawnRule(
        String mobId,
        int intervalDays,
        boolean onDayStart,
        DistanceRange distanceFromPlayer,
        CountRange count,
        int maxGlobal
) implements SpawnRule {

    @Override
    public String type() {
        return "day_interval";
    }
}
