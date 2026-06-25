package emaki.jiuwu.craft.corelib.gui;

public interface GuiSessionHandler {

    default void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
    }

    default void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
    }

    default void onDrag(GuiSession session, GuiDragContext drag) {
    }

    default void onClose(GuiSession session, GuiCloseContext close) {
    }
}
