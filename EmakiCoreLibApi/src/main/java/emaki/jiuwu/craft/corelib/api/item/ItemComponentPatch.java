package emaki.jiuwu.craft.corelib.api.item;

import java.util.Objects;

/** A version-independent mutation for one vanilla item data component. */
public final class ItemComponentPatch {

    public enum Operation {
        SET,
        UNSET,
        RESET
    }

    private final Operation operation;
    private final Object value;

    public ItemComponentPatch(Operation operation, Object value) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.value = operation == Operation.SET ? PlainItemData.copy(value) : null;
    }

    public static ItemComponentPatch set(Object value) {
        return new ItemComponentPatch(Operation.SET, value);
    }

    public static ItemComponentPatch unset() {
        return new ItemComponentPatch(Operation.UNSET, null);
    }

    public static ItemComponentPatch reset() {
        return new ItemComponentPatch(Operation.RESET, null);
    }

    public Operation operation() {
        return operation;
    }

    public Object value() {
        return PlainItemData.copy(value);
    }

    ItemComponentPatch copy() {
        return new ItemComponentPatch(operation, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemComponentPatch patch)) {
            return false;
        }
        return operation == patch.operation && Objects.equals(value, patch.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, value);
    }

    @Override
    public String toString() {
        return "ItemComponentPatch[operation=" + operation + ", value=" + value + "]";
    }
}
