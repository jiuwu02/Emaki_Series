package emaki.jiuwu.craft.structure.selection;

import java.util.Optional;

public final class SelectionSession {

    private final String worldName;
    private long lastTouchedNanos;
    private Corner first;
    private Corner second;

    public SelectionSession(String worldName) {
        this.worldName = worldName;
        this.lastTouchedNanos = System.nanoTime();
    }

    public String worldName() {
        return worldName;
    }

    public boolean hasBothCorners() {
        return first != null && second != null;
    }

    public boolean hasWorld() {
        return worldName != null;
    }

    public Optional<Corner> first() {
        return Optional.ofNullable(first);
    }

    public Optional<Corner> second() {
        return Optional.ofNullable(second);
    }

    public void setFirst(Corner corner) {
        this.first = corner;
        touch();
    }

    public void setSecond(Corner corner) {
        this.second = corner;
        touch();
    }

    public void restore(Corner previousFirst, Corner previousSecond) {
        this.first = previousFirst;
        this.second = previousSecond;
        touch();
    }

    public Optional<SelectionBox> box() {
        if (first == null || second == null) {
            return Optional.empty();
        }
        return Optional.of(SelectionBox.of(first.x(), first.y(), first.z(),
                second.x(), second.y(), second.z()));
    }

    public void replaceBox(SelectionBox box) {
        this.first = new Corner(box.minX(), box.minY(), box.minZ());
        this.second = new Corner(box.maxX(), box.maxY(), box.maxZ());
        touch();
    }

    public void touch() {
        lastTouchedNanos = System.nanoTime();
    }

    public long idleNanos() {
        return System.nanoTime() - lastTouchedNanos;
    }

    public record Corner(int x, int y, int z) {

        public String format() {
            return x + ", " + y + ", " + z;
        }
    }
}
