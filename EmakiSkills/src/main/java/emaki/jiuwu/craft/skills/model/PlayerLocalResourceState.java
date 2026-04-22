package emaki.jiuwu.craft.skills.model;

public final class PlayerLocalResourceState {

    private String resourceId;
    private double currentValue;
    private long lastRegenAt;

    public PlayerLocalResourceState(String resourceId, double currentValue, long lastRegenAt) {
        this.resourceId = resourceId == null ? "" : resourceId;
        this.currentValue = currentValue;
        this.lastRegenAt = lastRegenAt;
    }

    public String resourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId == null ? "" : resourceId;
    }

    public double currentValue() {
        return currentValue;
    }

    public void setCurrentValue(double currentValue) {
        this.currentValue = currentValue;
    }

    public long lastRegenAt() {
        return lastRegenAt;
    }

    public void setLastRegenAt(long lastRegenAt) {
        this.lastRegenAt = lastRegenAt;
    }
}
