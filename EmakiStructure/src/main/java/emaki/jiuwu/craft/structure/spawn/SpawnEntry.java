package emaki.jiuwu.craft.structure.spawn;

public record SpawnEntry(String type,
                         Mode mode,
                         int[] pos,
                         int radius,
                         int[] regionMin,
                         int[] regionMax,
                         int count,
                         double chance,
                         String facing,
                         Integer level) {

    public enum Mode {
        FIXED, SPHERE, REGION
    }

    public SpawnEntry {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (mode == null) {
            throw new NullPointerException("mode");
        }
        pos = pos == null ? new int[0] : pos.clone();
        regionMin = regionMin == null ? new int[0] : regionMin.clone();
        regionMax = regionMax == null ? new int[0] : regionMax.clone();
    }

    @Override
    public int[] pos() {
        return pos.clone();
    }

    @Override
    public int[] regionMin() {
        return regionMin.clone();
    }

    @Override
    public int[] regionMax() {
        return regionMax.clone();
    }
}
