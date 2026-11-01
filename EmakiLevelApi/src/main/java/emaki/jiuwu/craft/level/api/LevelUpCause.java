package emaki.jiuwu.craft.level.api;

/**
 * Attribution recorded when a level-up runs, carried into the level-up events and action
 * placeholders.
 *
 * <p>{@link LevelOperations#levelUp} treats a {@code null} cause as {@link #API}, so an omitted
 * cause is reported as an API-requested upgrade.
 */
public enum LevelUpCause {

    /** The runtime upgraded automatically after experience met the requirement. */
    AUTO,

    /** The player requested the upgrade themselves from the level GUI or the level-up command. */
    MANUAL,

    /** The upgrade is attributed to a command. */
    COMMAND,

    /** The level action pipeline dispatched the upgrade. */
    ACTION,

    /** A caller requested the upgrade through {@link LevelOperations#levelUp}. */
    API
}
