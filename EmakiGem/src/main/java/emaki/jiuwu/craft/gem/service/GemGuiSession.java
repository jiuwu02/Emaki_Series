package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private boolean returnTargetOnClose = true;
    private final List<ItemStack> upgradeMaterials = new ArrayList<>();
    private boolean processing;
    private PendingOperation pendingOperation = PendingOperation.none();
    private boolean rerollCompletedOnce;
    private boolean rerollRestartAcknowledged;

    GemGuiSession(Player player) {
        this.player = player;
    }

    public boolean rerollCompletedOnce() {
        return rerollCompletedOnce;
    }

    public void markRerollCompleted() {
        this.rerollCompletedOnce = true;
        this.rerollRestartAcknowledged = false;
    }

    public boolean rerollRestartAcknowledged() {
        return rerollRestartAcknowledged;
    }

    public void setRerollRestartAcknowledged(boolean rerollRestartAcknowledged) {
        this.rerollRestartAcknowledged = rerollRestartAcknowledged;
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

    public boolean rerollMode() {
        return mode == GemGuiMode.REROLL_FULL || mode == GemGuiMode.REROLL_VALUE;
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
        this.targetItem = targetItem;
        clearPendingOperation();
    }

    public boolean returnTargetOnClose() {
        return returnTargetOnClose;
    }

    public void setReturnTargetOnClose(boolean returnTargetOnClose) {
        this.returnTargetOnClose = returnTargetOnClose;
    }


    public void setTargetItemPreservingPending(ItemStack targetItem) {
        setTargetItem(targetItem, false);
    }

    private void setTargetItem(ItemStack targetItem, boolean clearPending) {
        this.targetItem = cloneNonAir(targetItem);
        if (clearPending) {
            clearPendingOperation();
        }
    }

    public ItemStack upgradeMaterial(int index) {
        return index < 0 || index >= upgradeMaterials.size() ? null : cloneNonAir(upgradeMaterials.get(index));
    }

    public List<ItemStack> upgradeMaterials() {
        if (upgradeMaterials.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copy = new ArrayList<>(upgradeMaterials.size());
        for (ItemStack itemStack : upgradeMaterials) {
            copy.add(cloneNonAir(itemStack));
        }
        return Collections.unmodifiableList(copy);
    }

    public void setUpgradeMaterial(int index, ItemStack itemStack) {
        if (index < 0) {
            return;
        }
        while (upgradeMaterials.size() <= index) {
            upgradeMaterials.add(null);
        }
        upgradeMaterials.set(index, cloneNonAir(itemStack));
        trimUpgradeMaterials();
    }

    public void setUpgradeMaterials(List<ItemStack> materials) {
        upgradeMaterials.clear();
        if (materials != null) {
            for (ItemStack material : materials) {
                upgradeMaterials.add(cloneNonAir(material));
            }
        }
        trimUpgradeMaterials();
    }

    public List<ItemStack> takeUpgradeMaterials() {
        List<ItemStack> materials = upgradeMaterials();
        upgradeMaterials.clear();
        return materials;
    }

    public boolean processing() {
        return processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
    }

    private void trimUpgradeMaterials() {
        while (!upgradeMaterials.isEmpty() && upgradeMaterials.get(upgradeMaterials.size() - 1) == null) {
            upgradeMaterials.remove(upgradeMaterials.size() - 1);
        }
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
