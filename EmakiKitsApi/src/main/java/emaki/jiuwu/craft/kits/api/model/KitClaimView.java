package emaki.jiuwu.craft.kits.api.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Outcome snapshot of a claim, purchase or redemption attempt.
 *
 * <p>{@code success} is the business outcome of an operation that actually ran. An unavailable
 * plugin is never encoded as a {@code KitClaimView}: it is returned as an
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult.Failure} of kind
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE} instead.
 *
 * @param kitId     the normalized kit id the operation targeted
 * @param periodKey the stable cycle key used for the idempotency boundary, or {@code "once"}
 * @param success   whether rewards were committed
 * @param reasonKey stable machine-readable reason key; {@code "success"} on success
 * @param auditIds  ids of the audit rows written for this operation
 */
public record KitClaimView(@NotNull String kitId,
                           @NotNull String periodKey,
                           boolean success,
                           @NotNull String reasonKey,
                           @NotNull List<String> auditIds) {

    /**
     * Creates a view with defensive copies.
     *
     * @throws NullPointerException when any string argument is {@code null}
     */
    public KitClaimView {
        if (kitId == null || periodKey == null || reasonKey == null) {
            throw new NullPointerException("kit claim view fields must not be null");
        }
        auditIds = auditIds == null ? List.of() : List.copyOf(auditIds);
    }
}
