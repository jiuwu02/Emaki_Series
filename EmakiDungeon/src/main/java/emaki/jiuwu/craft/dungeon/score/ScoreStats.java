package emaki.jiuwu.craft.dungeon.score;

public record ScoreStats(double damage, long kills, double heal, long death, double defense) {

    public static ScoreStats zero() {
        return new ScoreStats(0.0D, 0L, 0.0D, 0L, 0.0D);
    }
}
