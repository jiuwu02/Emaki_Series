package emaki.jiuwu.craft.station.config;

public record GuiSettings(int clickThrottleMs, long refreshTicks) {

    public static GuiSettings defaults() {
        return new GuiSettings(200, 20L);
    }

    public GuiSettings normalized() {
        return new GuiSettings(Math.clamp(clickThrottleMs, 0, 5_000),
                Math.clamp(refreshTicks, 5L, 200L));
    }
}
