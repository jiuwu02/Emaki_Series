package emaki.jiuwu.craft.corelib.display;

public interface DisplayRuntimeSettings {

    double viewDistanceBlocks();

    int refreshIntervalTicks();

    static DisplayRuntimeSettings of(double viewDistanceBlocks, int refreshIntervalTicks) {
        double distance = Math.max(1D, viewDistanceBlocks);
        int interval = Math.max(1, refreshIntervalTicks);
        return new DisplayRuntimeSettings() {
            @Override
            public double viewDistanceBlocks() {
                return distance;
            }

            @Override
            public int refreshIntervalTicks() {
                return interval;
            }
        };
    }
}
