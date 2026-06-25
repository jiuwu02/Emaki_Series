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

    /**
     * Derives the sound-facing click type from a vanilla container click packet.
     *
     * <p>The packet backend has no {@link InventoryClickEvent}; it only sees the
     * window-click {@code button} and {@code mode} fields. This mirrors the
     * three-state contract of {@link #from(InventoryClickEvent)} so slot click
     * sounds resolve identically across backends. Only {@code mode 0} (normal
     * pickup) distinguishes left/right by button; every other mode (shift,
     * number key, drop, drag, double click, ...) falls back to the neutral
     * {@link #CLICK} bucket, matching how {@link GuiSlot#soundFor} treats
     * non-left/right clicks.</p>
     *
     * @param mode the window-click mode field
     * @param button the window-click button field
     */
    public static GuiClickType from(int mode, int button) {
        if (mode == 0) {
            return button == 1 ? RIGHTCLICK : LEFTCLICK;
        }
        return CLICK;
    }
}
