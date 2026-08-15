package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface GuiClickContext {

    Player viewer();

    boolean isTopInventory();

    GuiClickType clickType();

    boolean isShiftClick();

    boolean isLeftClick();

    boolean isRightClick();

    boolean isBlockedTransfer();

    boolean isMoveToOtherInventory();

    ItemStack cursorItem();

    void setCursor(ItemStack item);

    ItemStack currentItem();

    ItemStack heldItem();

    void setHeldItem(ItemStack item);

    boolean isUnsupportedKeyboardClick();

    void clearClickedSlot();

    void setCancelled(boolean cancelled);
}
