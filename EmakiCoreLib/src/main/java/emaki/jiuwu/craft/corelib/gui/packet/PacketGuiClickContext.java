package emaki.jiuwu.craft.corelib.gui.packet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;


















final class PacketGuiClickContext implements GuiClickContext {

    private static final int OFFHAND_BUTTON = 40;

    private final Player viewer;
    private final PacketGuiBackend.PacketWindow window;
    private final WrapperPlayClientClickWindow packet;
    private final boolean topInventory;
    private final PacketGuiBackend backend;

    PacketGuiClickContext(Player viewer,
            PacketGuiBackend.PacketWindow window,
            WrapperPlayClientClickWindow packet,
            boolean topInventory,
            PacketGuiBackend backend) {
        this.viewer = viewer;
        this.window = window;
        this.packet = packet;
        this.topInventory = topInventory;
        this.backend = backend;
    }

    private WindowClickType mode() {
        return packet.getWindowClickType();
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
        return GuiClickType.from(mode().ordinal(), packet.getButton());
    }

    @Override
    public boolean isShiftClick() {
        return mode() == WindowClickType.QUICK_MOVE;
    }

    @Override
    public boolean isLeftClick() {
        return mode() == WindowClickType.PICKUP && packet.getButton() == 0;
    }

    @Override
    public boolean isRightClick() {
        return mode() == WindowClickType.PICKUP && packet.getButton() == 1;
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
        ItemStack cursor = window.cursor();
        return cursor == null ? null : cursor.clone();
    }

    @Override
    public void setCursor(ItemStack item) {
        window.setCursor(item == null ? null : item.clone());
    }

    @Override
    public ItemStack currentItem() {
        int rawSlot = packet.getSlot();
        if (topInventory) {
            return window.topItem(rawSlot);
        }
        return playerSlotItem(rawSlot);
    }

    @Override
    public ItemStack heldItem() {
        if (mode() == WindowClickType.SWAP) {
            int button = packet.getButton();
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
            int button = packet.getButton();
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
        int rawSlot = packet.getSlot();
        int playerSlot = toPlayerInventorySlot(rawSlot);
        if (playerSlot >= 0) {
            viewer.getInventory().setItem(playerSlot, null);
        }
    }

    private ItemStack playerSlotItem(int rawSlot) {
        int playerSlot = toPlayerInventorySlot(rawSlot);
        return playerSlot >= 0 ? viewer.getInventory().getItem(playerSlot) : null;
    }

    @Override
    public void setCancelled(boolean cancelled) {


    }






    private int toPlayerInventorySlot(int rawSlot) {
        int offset = rawSlot - window.topSize();
        if (offset < 0 || offset >= 36) {
            return -1;
        }

        if (offset < 27) {
            return offset + 9;
        }
        return offset - 27;
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
