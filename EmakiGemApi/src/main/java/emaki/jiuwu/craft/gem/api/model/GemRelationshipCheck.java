package emaki.jiuwu.craft.gem.api.model;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * Outcome of checking whether a gem may be inlaid into, or extracted from, a piece of equipment.
 *
 * <p>A rejection is a legitimate business answer rather than a failed call, so the catalog wraps this
 * in a successful {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult} and callers inspect
 * {@link #allowed()}.
 *
 * @param allowed      whether the relationship rules permit the operation
 * @param reasonKey    stable machine-readable key explaining a rejection; empty when allowed
 * @param placeholders substitution data for rendering the reason; empty when allowed
 */
public record GemRelationshipCheck(boolean allowed,
                                   @NotNull String reasonKey,
                                   @NotNull Map<String, Object> placeholders) {

    private static final GemRelationshipCheck ALLOWED = new GemRelationshipCheck(true, "", Map.of());

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param allowed      whether the operation is permitted
     * @param reasonKey    rejection key
     * @param placeholders substitution data
     */
    public GemRelationshipCheck {
        reasonKey = reasonKey == null ? "" : reasonKey;
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
    }

    /** {@return the shared value describing a permitted operation} */
    public static @NotNull GemRelationshipCheck pass() {
        return ALLOWED;
    }

    /**
     * Creates a rejection.
     *
     * @param reasonKey    stable machine-readable key explaining the rejection
     * @param placeholders substitution data for rendering the reason
     * @return the rejection
     */
    public static @NotNull GemRelationshipCheck deny(@NotNull String reasonKey,
                                                     @NotNull Map<String, Object> placeholders) {
        return new GemRelationshipCheck(false, reasonKey, placeholders);
    }
}
