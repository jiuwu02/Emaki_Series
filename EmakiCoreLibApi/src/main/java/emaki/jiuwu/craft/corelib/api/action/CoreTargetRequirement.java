package emaki.jiuwu.craft.corelib.api.action;

/**
 * What a stage needs from the target flow.
 *
 * <p>Declaring this replaces the {@code requirePlayerResult(context)} boilerplate that nearly every
 * v1 builtin action repeated. CoreLib checks the requirement once, before the stage runs.</p>
 */
public enum CoreTargetRequirement {

    /** No target at all. Broadcast and console-command style stages. */
    NONE,

    /** Runs with or without targets. With zero targets the stage still runs once. */
    OPTIONAL,

    /** Needs at least one entity target. Zero entities yields {@code Skipped}. */
    REQUIRED_ENTITY,

    /** Needs at least one location target. */
    REQUIRED_LOCATION,

    /** Needs at least one target of either shape. */
    REQUIRED_ANY;

    /** {@return whether this requirement rejects an empty target flow} */
    public boolean requiresTarget() {
        return this == REQUIRED_ENTITY || this == REQUIRED_LOCATION || this == REQUIRED_ANY;
    }

    /**
     * Tests whether {@code subject} satisfies this requirement.
     *
     * @param subject candidate subject
     * @return true when the subject shape is acceptable
     */
    public boolean accepts(CoreActionSubject subject) {
        if (subject == null) {
            return this == NONE || this == OPTIONAL;
        }
        return switch (this) {
            case NONE, OPTIONAL -> true;
            case REQUIRED_ENTITY -> subject instanceof CoreActionSubject.OfEntity;
            case REQUIRED_LOCATION -> subject instanceof CoreActionSubject.OfLocation
                    || subject instanceof CoreActionSubject.OfEntity;
            case REQUIRED_ANY -> !(subject instanceof CoreActionSubject.Absent);
        };
    }
}
