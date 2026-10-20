package emaki.jiuwu.craft.dungeon.score;

public record ScoreWeights(double damageWeight,
        double killWeight,
        double healWeight,
        double deathWeight,
        double defenseWeight,
        double max) {

    public static ScoreWeights defaults() {
        return new ScoreWeights(1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 100.0D);
    }

    public double totalWeight() {
        return damageWeight + killWeight + healWeight + deathWeight + defenseWeight;
    }
}
