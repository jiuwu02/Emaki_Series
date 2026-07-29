package emaki.jiuwu.craft.corelib.api.contract;

/**
 * Payload placeholder for operations that succeed without producing a value.
 *
 * <p>{@link EmakiResult} forbids {@code null} payloads, so void-like operations return
 * {@code EmakiResult<Unit>} and use {@link EmakiResult#ok()} on success. This keeps pattern matching
 * exhaustive without introducing a nullable payload.
 */
public enum Unit {

    /** The single instance. */
    INSTANCE
}
