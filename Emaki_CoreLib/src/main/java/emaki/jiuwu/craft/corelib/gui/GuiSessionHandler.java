package emaki.jiuwu.craft.corelib.gui;

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
}
