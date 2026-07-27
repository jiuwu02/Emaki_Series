package emaki.jiuwu.craft.corelib.gui.packet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;

final class PacketGuiClickContext implements GuiClickContext {

    private static final int OFFHAND_BUTTON = 40;

    private final Player viewer;
    private final PacketGuiBackend.PacketWindow window;
    private final PacketGuiBackend.ClickSnapshot click;
    private final int containerTopSize;
    private final boolean topInventory;

    PacketGuiClickContext(Player viewer,
            PacketGuiBackend.PacketWindow window,
            PacketGuiBackend.ClickSnapshot click,
            int containerTopSize,
            boolean topInventory) {
        this.viewer = viewer;
        this.window = window;
        this.click = click;
        this.containerTopSize = containerTopSize;
        this.topInventory = topInventory;
    }

    private WindowClickType mode() {
        return click.clickType();
    }

    @Override
    public Player viewer() {
        return viewer;
    }

    @Override
    public boolean isTopInventory() {
        return topInventory;
    }

    @Override
    public GuiClickType clickType() {
        return switch (mode()) {
            case PICKUP -> click.button() == 1
                    ? GuiClickType.RIGHTCLICK
                    : GuiClickType.LEFTCLICK;
            case QUICK_MOVE -> click.button() == 1
                    ? GuiClickType.SHIFT_RIGHTCLICK
                    : GuiClickType.SHIFT_LEFTCLICK;
            case SWAP -> click.button() == OFFHAND_BUTTON
                    ? GuiClickType.SWAP_OFFHAND
                    : GuiClickType.NUMBER_KEY;
            case CLONE -> GuiClickType.MIDDLECLICK;
            case THROW -> click.button() == 1
                    ? GuiClickType.CONTROL_DROP
                    : GuiClickType.DROP;
            case PICKUP_ALL -> GuiClickType.DOUBLECLICK;
            case QUICK_CRAFT, UNKNOWN -> GuiClickType.CLICK;
        };
    }

    @Override
    public boolean isShiftClick() {
        return mode() == WindowClickType.QUICK_MOVE;
    }

    @Override
    public boolean isLeftClick() {
        return (mode() == WindowClickType.PICKUP || mode() == WindowClickType.QUICK_MOVE)
                && click.button() == 0;
    }

    @Override
    public boolean isRightClick() {
        return (mode() == WindowClickType.PICKUP || mode() == WindowClickType.QUICK_MOVE)
                && click.button() == 1;
    }

    @Override
    public boolean isBlockedTransfer() {
        return mode() == WindowClickType.QUICK_MOVE
                || mode() == WindowClickType.PICKUP_ALL;
    }

    @Override
    public boolean isMoveToOtherInventory() {
        return mode() == WindowClickType.QUICK_MOVE;
    }

    @Override
    public ItemStack cursorItem() {
        return clone(window.cursor());
    }

    @Override
    public void setCursor(ItemStack item) {
        window.setCursor(clone(item));
    }

    @Override
    public ItemStack currentItem() {
        if (topInventory) {
            return window.topItem(click.rawSlot());
        }
        return playerSlotItem(click.rawSlot());
    }

    @Override
    public ItemStack heldItem() {
        if (mode() == WindowClickType.SWAP) {
            int button = click.button();
            if (button == OFFHAND_BUTTON) {
                return clone(viewer.getInventory().getItemInOffHand());
            }
            if (button >= 0 && button < 9) {
                return clone(viewer.getInventory().getItem(button));
            }
            return null;
        }
        return cursorItem();
    }

    @Override
    public void setHeldItem(ItemStack item) {
        if (mode() == WindowClickType.SWAP) {
            int button = click.button();
            if (button == OFFHAND_BUTTON) {
                viewer.getInventory().setItemInOffHand(item);
                return;
            }
            if (button >= 0 && button < 9) {
                viewer.getInventory().setItem(button, item);
            }
            return;
        }
        setCursor(item);
    }

    @Override
    public boolean isUnsupportedKeyboardClick() {
        return mode() == WindowClickType.THROW;
    }

    @Override
    public void clearClickedSlot() {
        if (topInventory) {
            return;
        }
        int playerSlot = toPlayerInventorySlot(click.rawSlot());
        if (playerSlot >= 0) {
            viewer.getInventory().setItem(playerSlot, null);
        }
    }

    @Override
    public void setCancelled(boolean cancelled) {
        // Managed packet clicks are cancelled before entity-thread dispatch. The
        // authoritative WindowItems response determines the visible result.
    }

    private ItemStack playerSlotItem(int rawSlot) {
        int playerSlot = toPlayerInventorySlot(rawSlot);
        return playerSlot >= 0 ? viewer.getInventory().getItem(playerSlot) : null;
    }

    private int toPlayerInventorySlot(int rawSlot) {
        int offset = rawSlot - containerTopSize;
        if (offset < 0 || offset >= 36) {
            return -1;
        }
        return offset < 27 ? offset + 9 : offset - 27;
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
