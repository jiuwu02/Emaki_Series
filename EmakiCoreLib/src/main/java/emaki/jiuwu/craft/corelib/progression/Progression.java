package emaki.jiuwu.craft.corelib.progression;

/**
 * Represents a progression curve that yields a value of type T for a given level.
 * <p>
 * Used to unify level requirement curves and skill upgrade success rate curves.
 *
 * @param <T> the value type (e.g., Double for exp requirements or success rates)
 */
public interface Progression<T> {

    /**
     * Returns the value at the specified level.
     *
     * @param level the level to query (typically 1-based)
     * @return the value at that level, or a fallback if undefined
     */
    T valueAt(int level);
}
