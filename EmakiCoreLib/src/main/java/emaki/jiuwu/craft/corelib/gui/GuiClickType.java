package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.event.inventory.InventoryClickEvent;

public enum GuiClickType {
    CLICK,
    LEFTCLICK,
    RIGHTCLICK;

    public static GuiClickType from(InventoryClickEvent event) {
        if (event == null) {
            return CLICK;
        }
        if (event.isRightClick()) {
            return RIGHTCLICK;
        }
        if (event.isLeftClick()) {
            return LEFTCLICK;
        }
        return CLICK;
    }
}
