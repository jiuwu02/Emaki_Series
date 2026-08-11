package emaki.jiuwu.craft.storage.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A batch of storage increments pre-checked and committed as one unit.
 *
 * <p>Ops are applied in list order and the same template may appear more than once; the runtime
 * accumulates rather than de-duplicates, because collapsing them would silently change the meaning of
 * a recipe that intentionally lists a material twice. Aggregate on the calling side when you want a
 * single op.
 *
 * @param ops          the increments, in application order; never {@code null}
 * @param allOrNothing when {@code true} a single failed pre-check aborts the whole batch and storage
 *                     is left byte-for-byte unchanged; when {@code false} each op is applied on a
 *                     best-effort basis and the result reports per-op amounts
 */
public record StorageBatchRequest(@NotNull List<StorageBatchOp> ops, boolean allOrNothing) {

    /** Copies the op list defensively and drops {@code null} entries. */
    public StorageBatchRequest {
        ops = ops == null ? List.of() : List.copyOf(ops.stream().filter(op -> op != null).toList());
    }

    /**
     * Creates an all-or-nothing request.
     *
     * @param ops the increments, in application order
     * @return the request
     */
    public static @NotNull StorageBatchRequest atomic(@Nullable List<StorageBatchOp> ops) {
        return new StorageBatchRequest(ops, true);
    }

    /**
     * Creates a best-effort request.
     *
     * @param ops the increments, in application order
     * @return the request
     */
    public static @NotNull StorageBatchRequest bestEffort(@Nullable List<StorageBatchOp> ops) {
        return new StorageBatchRequest(ops, false);
    }

    /** {@return whether this request carries no ops} */
    public boolean empty() {
        return ops.isEmpty();
    }
}
