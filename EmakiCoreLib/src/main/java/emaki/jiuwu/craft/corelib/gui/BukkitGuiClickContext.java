package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;









final class BukkitGuiClickContext implements GuiClickContext {

    private final InventoryClickEvent event;

    BukkitGuiClickContext(InventoryClickEvent event) {
        this.event = event;
    }

    private Player player() {
        HumanEntity who = event.getWhoClicked();
        return who instanceof Player p ? p : null;
    }

    @Override
    public Player viewer() {
        return player();
    }

    @Override
    public boolean isTopInventory() {
        Inventory clicked = event.getClickedInventory();
        return clicked != null && clicked.equals(event.getView().getTopInventory());
    }

    @Override
    public GuiClickType clickType() {
        return GuiClickType.from(event);
    }

    @Override
    public boolean isShiftClick() {
        return event.isShiftClick();
    }

    @Override
    public boolean isLeftClick() {
        return event.isLeftClick();
    }

    @Override
    public boolean isRightClick() {
        return event.isRightClick();
    }

    @Override
    public boolean isBlockedTransfer() {
        return event.isShiftClick()
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == ClickType.DOUBLE_CLICK;
    }

    @Override
    public boolean isMoveToOtherInventory() {
        return event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
    }

    @Override
    public ItemStack cursorItem() {
        return event.getCursor();
    }

    @Override
    public void setCursor(ItemStack item) {
        Player player = player();
        if (player != null) {
            player.setItemOnCursor(item);
        }
    }

    @Override
    public ItemStack currentItem() {
        return event.getCurrentItem();
    }

    @Override
    public ItemStack heldItem() {
        Player player = player();
        if (player == null) {
            return null;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton < 0 || hotbarButton >= 9) {
                return null;
            }
            return player.getInventory().getItem(hotbarButton);
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return player.getInventory().getItemInOffHand();
        }
        return event.getCursor();
    }

    @Override
    public void setHeldItem(ItemStack item) {
        Player player = player();
        if (player == null) {
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < 9) {
                player.getInventory().setItem(hotbarButton, item);
            }
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            player.getInventory().setItemInOffHand(item);
            return;
        }
        player.setItemOnCursor(item);
    }

    @Override
    public boolean isUnsupportedKeyboardClick() {
        if (!event.getClick().isKeyboardClick()) {
            return false;
        }
        return event.getClick() != ClickType.NUMBER_KEY && event.getClick() != ClickType.SWAP_OFFHAND;
    }

    @Override
    public void clearClickedSlot() {
        Inventory clicked = event.getClickedInventory();
        if (clicked != null) {
            clicked.setItem(event.getSlot(), null);
        }
    }

    @Override
    public void setCancelled(boolean cancelled) {
        event.setCancelled(cancelled);
    }
}
