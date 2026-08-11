package emaki.jiuwu.craft.forge.api.model;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * Outcome of checking whether a player may forge a recipe with a given item layout.
 *
 * <p>A rejection is a legitimate business answer, not a failure of the API call, so
 * {@code ForgeCatalog.validate} wraps this in a successful
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult} and callers inspect {@link #allowed()}.
 *
 * @param allowed      whether the attempt would be accepted
 * @param reasonKey    stable machine-readable key explaining a rejection; empty when allowed
 * @param placeholders substitution data for rendering the reason; empty when allowed
 */
public record ForgeValidation(boolean allowed,
                              @NotNull String reasonKey,
                              @NotNull Map<String, Object> placeholders) {

    private static final ForgeValidation ALLOWED = new ForgeValidation(true, "", Map.of());

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param allowed      whether the attempt would be accepted
     * @param reasonKey    rejection key
     * @param placeholders substitution data
     */
    public ForgeValidation {
        reasonKey = reasonKey == null ? "" : reasonKey;
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
    }

    /** {@return the shared value describing an accepted attempt} */
    public static @NotNull ForgeValidation pass() {
        return ALLOWED;
    }

    /**
     * Creates a rejection.
     *
     * @param reasonKey    stable machine-readable key explaining the rejection
     * @param placeholders substitution data for rendering the reason
     * @return the rejection
     */
    public static @NotNull ForgeValidation deny(@NotNull String reasonKey,
                                                @NotNull Map<String, Object> placeholders) {
        return new ForgeValidation(false, reasonKey, placeholders);
    }
}
