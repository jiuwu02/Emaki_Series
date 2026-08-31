package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;

public class PityState {
    private int counter;
    private long lastTriggerTime;
    private boolean triggered;

    public PityState() {
        this.counter = 0;
        this.lastTriggerTime = 0L;
        this.triggered = false;
    }

    public PityState(int counter, long lastTriggerTime, boolean triggered) {
        this.counter = counter;
        this.lastTriggerTime = lastTriggerTime;
        this.triggered = triggered;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public void incrementCounter() {
        this.counter++;
    }

    public void decrementCounter(int amount) {
        this.counter = Math.max(0, this.counter - amount);
    }

    public void resetCounter() {
        this.counter = 0;
    }

    public long getLastTriggerTime() {
        return lastTriggerTime;
    }

    public void setLastTriggerTime(long lastTriggerTime) {
        this.lastTriggerTime = lastTriggerTime;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public @NotNull PityState copy() {
        return new PityState(this.counter, this.lastTriggerTime, this.triggered);
    }

    @Override
    public String toString() {
        return "PityState{counter=" + counter + ", lastTriggerTime=" + lastTriggerTime + ", triggered=" + triggered + "}";
    }
}
