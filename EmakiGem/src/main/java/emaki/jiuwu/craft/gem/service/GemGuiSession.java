package emaki.jiuwu.craft.gem.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;

final class GemGuiSession implements GemPlayerGuiSession {

    private final Player player;
    private GuiSession guiSession;
    private GemGuiMode mode = GemGuiMode.INLAY;
    private String currentTemplateId = "";
    private boolean templateSwitching;
    private ItemStack targetItem;
    private PendingOperation pendingOperation = PendingOperation.none();

    GemGuiSession(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    public GuiSession guiSession() {
        return guiSession;
    }

    public void setGuiSession(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    public GemGuiMode mode() {
        return mode;
    }

    public void setMode(GemGuiMode mode) {
        if (mode != null) {
            this.mode = mode;
            clearPendingOperation();
        }
    }

    public String currentTemplateId() {
        return currentTemplateId;
    }

    public void setCurrentTemplateId(String currentTemplateId) {
        this.currentTemplateId = currentTemplateId == null ? "" : currentTemplateId.trim();
    }

    public boolean templateSwitching() {
        return templateSwitching;
    }

    public void setTemplateSwitching(boolean templateSwitching) {
        this.templateSwitching = templateSwitching;
    }

    public ItemStack targetItem() {
        return cloneNonAir(targetItem);
    }

    public ItemStack mutableTargetItem() {
        return targetItem;
    }

    public void setTargetItem(ItemStack targetItem) {
        this.targetItem = cloneNonAir(targetItem);
        clearPendingOperation();
    }

    public PendingOperation pendingOperation() {
        return pendingOperation;
    }

    public void setPendingOperation(PendingOperation pendingOperation) {
        this.pendingOperation = pendingOperation == null ? PendingOperation.none() : pendingOperation;
    }

    public void clearPendingOperation() {
        this.pendingOperation = PendingOperation.none();
    }

    public enum PendingType {
        NONE,
        INLAY,
        EXTRACT
    }

    public record PendingOperation(PendingType type, int slotIndex, ItemStack inputItem) {

        public PendingOperation {
            type = type == null ? PendingType.NONE : type;
            inputItem = cloneNonAir(inputItem);
        }

        public static PendingOperation none() {
            return new PendingOperation(PendingType.NONE, -1, null);
        }

        public boolean active() {
            return type != PendingType.NONE && slotIndex >= 0;
        }
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        return InventoryItemUtil.cloneNonAir(itemStack);
    }
}
