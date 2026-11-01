package emaki.jiuwu.craft.level.api;

/**
 * Identifies which level operation a {@link LevelOperationResult} describes.
 *
 * <p>The constants mirror the mutating entry points of {@link LevelOperations} one to one, and
 * the level action pipeline dispatches its operations by this type as well.
 */
public enum LevelOperationType {

    /** Experience granted through {@code addExp}. */
    ADD_EXP,

    /** Absolute experience write through {@code setExp}. */
    SET_EXP,

    /** Experience subtracted through {@code removeExp}. */
    REMOVE_EXP,

    /** Direct level raise through {@code addLevel}, bypassing requirements and costs. */
    ADD_LEVEL,

    /** Absolute level write through {@code setLevel}, bypassing requirements and costs. */
    SET_LEVEL,

    /** Direct level lower through {@code removeLevel}. */
    REMOVE_LEVEL,

    /** One regular upgrade through {@code levelUp} and the full upgrade pipeline. */
    LEVEL_UP,

    /** The level type cleared back to its configured starting state through {@code reset}. */
    RESET
}
