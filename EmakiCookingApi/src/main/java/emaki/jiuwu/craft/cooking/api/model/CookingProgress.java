package emaki.jiuwu.craft.cooking.api.model;

/**
 * How far a station has advanced through its current recipe.
 *
 * @param current   ticks or steps completed
 * @param target    ticks or steps required; {@code 0} when no recipe is running
 * @param completed whether the station has finished and is waiting to be collected
 */
public record CookingProgress(int current, int target, boolean completed) {

    private static final CookingProgress IDLE = new CookingProgress(0, 0, false);

    /**
     * Clamps both counters so neither can be negative.
     *
     * @param current   completed amount
     * @param target    required amount
     * @param completed whether finished
     */
    public CookingProgress {
        current = Math.max(0, current);
        target = Math.max(0, target);
    }

    /** {@return the shared value describing a station with nothing in progress} */
    public static CookingProgress idle() {
        return IDLE;
    }

    /** {@return whether a recipe is currently running} */
    public boolean running() {
        return target > 0 && !completed;
    }

    /**
     * {@return progress as a fraction in {@code [0, 1]}; {@code 0} when no recipe is running, and
     * {@code 1} once completed}
     */
    public double fraction() {
        if (completed) {
            return 1.0D;
        }
        if (target <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) current / target);
    }
}
