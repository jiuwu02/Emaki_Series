package emaki.jiuwu.craft.level.model;

public final class PlayerLevelEntry {

    private int level;
    private double exp;
    private double totalExp;
    private long updatedAt;

    public PlayerLevelEntry(int level, double exp, double totalExp, long updatedAt) {
        this.level = level;
        this.exp = Math.max(0D, exp);
        this.totalExp = Math.max(0D, totalExp);
        this.updatedAt = updatedAt;
    }

    public PlayerLevelEntry copy() {
        return new PlayerLevelEntry(level, exp, totalExp, updatedAt);
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = level;
        touch();
    }

    public double exp() {
        return exp;
    }

    public void exp(double exp) {
        this.exp = Math.max(0D, exp);
        touch();
    }

    public double totalExp() {
        return totalExp;
    }

    public void totalExp(double totalExp) {
        this.totalExp = Math.max(0D, totalExp);
        touch();
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }
}
