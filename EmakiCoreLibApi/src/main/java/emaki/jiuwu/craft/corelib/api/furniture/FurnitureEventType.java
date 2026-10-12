package emaki.jiuwu.craft.corelib.api.furniture;

/**
 * Normalised furniture event kinds across third-party furniture sources.
 */
public enum FurnitureEventType {

    /** A player interacted with furniture (right click unless the event reports otherwise). */
    INTERACT,

    /** Furniture was placed into the world. */
    PLACE,

    /** Furniture was broken or removed. */
    BREAK,

    /**
     * A player hit furniture without breaking it (CraftEngine only). Subscribers interpret this on
     * their own: EmakiStructure treats it as a member change, EmakiCooking as a left click.
     */
    HIT
}
