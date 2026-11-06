package emaki.jiuwu.craft.dungeon.score;

public record ScoreWeights(double damageWeight,
        double killWeight,
        double healWeight,
        double deathWeight,
        double defenseWeight,
        double timeWeight,
        double targetTimeSeconds,
        double max) {

    public static ScoreWeights defaults() {
        return new ScoreWeights(1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 0.0D, 0.0D, 100.0D);
    }

    public double totalWeight() {
        return damageWeight + killWeight + healWeight + deathWeight + defenseWeight
                + Math.max(0.0D, timeWeight) * (targetTimeSeconds > 0.0D ? 1.0D : 0.0D);
    }
}
