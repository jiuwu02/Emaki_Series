package emaki.jiuwu.craft.corelib.api.action;

/**
 * The verdict of one target condition test.
 *
 * <p>{@link #UNKNOWN} is deliberately separate from {@link #FAIL}: a subject that cannot answer the
 * question at all — a non-player asked for its experience level, or any entity asked for an EmakiMobs id
 * while that plugin is absent — is not a target that broke a rule. The selector decides what an
 * unanswered condition means through its {@code invalid_as_failure} flag, matching the condition block
 * semantics used elsewhere in the library.</p>
 */
public enum CoreTargetOutcome {

    /** The subject satisfies the condition. */
    PASS,

    /** The subject does not satisfy the condition. */
    FAIL,

    /** The condition cannot be answered for this subject. */
    UNKNOWN;

    /** {@return whether this verdict is a satisfied condition} */
    public boolean passed() {
        return this == PASS;
    }
}