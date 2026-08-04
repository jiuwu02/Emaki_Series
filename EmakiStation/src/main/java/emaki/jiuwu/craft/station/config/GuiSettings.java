package emaki.jiuwu.craft.station.config;

/**
 * GUI timing settings from {@code config.yml}.
 *
 * @param clickThrottleMs the minimum gap between two accepted clicks in one session
 * @param refreshTicks    how often an open session re-renders its progress display
 */
public record GuiSettings(int clickThrottleMs, long refreshTicks) {

    /** {@return the shipped defaults} */
    public static GuiSettings defaults() {
        return new GuiSettings(200, 20L);
    }

    /**
     * Clamps every field into its documented range.
     *
     * @return a settings record whose values are all in range
     */
    public GuiSettings normalized() {
        return new GuiSettings(Math.clamp(clickThrottleMs, 0, 5_000),
                Math.clamp(refreshTicks, 5L, 200L));
    }
}
