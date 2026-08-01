package emaki.jiuwu.craft.corelib.api.action.v2;

/**
 * Value shapes a stage parameter can declare.
 *
 * <p>Extends the v1 five-value set with the types that v1 stages parsed by hand inside
 * {@code execute}, so validation moves to config load time.</p>
 */
public enum CoreStageParameterType {

    /** Free text. */
    STRING,

    /** Whole number. */
    INTEGER,

    /** Decimal number. */
    DOUBLE,

    /** {@code true} / {@code false}. */
    BOOLEAN,

    /** Legacy tick/second form kept for v1 parity. */
    TIME,

    /** A Bukkit {@code EntityType} name. */
    ENTITY_TYPE,

    /** A Bukkit {@code Material} name. */
    MATERIAL,

    /** A sound key, either a Bukkit {@code Sound} name or a {@code namespace:key} string. */
    SOUND,

    /** A duration such as {@code 10t}, {@code 500ms} or {@code 2s}. */
    DURATION,

    /** A probability such as {@code 50%}, {@code 0.5} or {@code 1/3}. */
    PERCENTAGE,

    /** An arithmetic expression evaluated by CoreLib's expression engine. */
    EXPRESSION
}
