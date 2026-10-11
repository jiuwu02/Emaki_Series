package emaki.jiuwu.craft.structure.multiblock;

import java.util.UUID;

public final class MultiblockInstance {

    public enum State {
        UNFORMED, FORMED
    }

    private final StructurePose pose;
    private final UUID owner;
    private final long createdAtNanos;
    private volatile State state;
    private volatile long lastChangeNanos;
    private volatile boolean everFormed;

    public MultiblockInstance(StructurePose pose, UUID owner) {
        this.pose = pose;
        this.owner = owner;
        this.createdAtNanos = System.nanoTime();
        this.lastChangeNanos = this.createdAtNanos;
        this.state = State.UNFORMED;
    }

    public StructurePose pose() {
        return pose;
    }

    public UUID owner() {
        return owner;
    }

    public State state() {
        return state;
    }

    public boolean formed() {
        return state == State.FORMED;
    }

    public boolean transitionToFormed() {
        if (state == State.FORMED) {
            return false;
        }
        state = State.FORMED;
        lastChangeNanos = System.nanoTime();
        return true;
    }

    public boolean degradeToUnformed() {
        if (state == State.UNFORMED) {
            return false;
        }
        state = State.UNFORMED;
        everFormed = true;
        lastChangeNanos = System.nanoTime();
        return true;
    }

    public boolean everFormed() {
        return everFormed;
    }

    public void touch() {
        lastChangeNanos = System.nanoTime();
    }

    public long idleNanos() {
        return System.nanoTime() - lastChangeNanos;
    }

    public long ageNanos() {
        return System.nanoTime() - createdAtNanos;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MultiblockInstance that && pose.equals(that.pose);
    }

    @Override
    public int hashCode() {
        return pose.hashCode();
    }

    @Override
    public String toString() {
        return pose.definitionId() + "@" + pose.anchorX() + "," + pose.anchorY() + "," + pose.anchorZ()
                + " " + state;
    }
}
