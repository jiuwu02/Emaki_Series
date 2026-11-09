package emaki.jiuwu.craft.corelib.item;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

public enum ClickRefreshDecision {
    SKIP,
    FULL_COLLECT_TO_CURSOR,
    FULL_DOUBLE_CLICK,
    FULL_UNKNOWN_ACTION,
    FULL_UNKNOWN_CLICK,
    FULL_UNSUPPORTED_CONTEXT,
    LOCAL;

    public static ClickRefreshDecision of(InventoryAction action, ClickType click) {
        if (action == null || click == null) {
            return FULL_UNSUPPORTED_CONTEXT;
        }
        if (action == InventoryAction.NOTHING
                || action == InventoryAction.CLONE_STACK
                || action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR) {
            return SKIP;
        }
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            return FULL_COLLECT_TO_CURSOR;
        }
        if (click == ClickType.DOUBLE_CLICK) {
            return FULL_DOUBLE_CLICK;
        }
        if (action == InventoryAction.UNKNOWN) {
            return FULL_UNKNOWN_ACTION;
        }
        if (click == ClickType.UNKNOWN) {
            return FULL_UNKNOWN_CLICK;
        }
        return LOCAL;
    }
}
