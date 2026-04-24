package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public interface GuiSessionHandler {

    default void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
    }

    default void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
    }

    default void onDrag(GuiSession session, InventoryDragEvent event) {
    }

    default void onClose(GuiSession session, InventoryCloseEvent event) {
    }

    static boolean isBlockedTransfer(InventoryClickEvent event) {
        if (event == null) {
            return false;
        }
        return event.isShiftClick()
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == ClickType.DOUBLE_CLICK;
    }
}
