package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;

final class StrengthenGuiSession {

    enum CompletionPhase {
        OPEN,
        PROCESSING,
        COMMITTED,
        RESULT_DELIVERED,
        ESCROW_CLEARED,
        COMPLETED
    }

    private static final int MATERIAL_SLOT_COUNT = 4;

    private final Player player;
    private final List<ItemStack> materialInputs = new ArrayList<>(MATERIAL_SLOT_COUNT);
    private GuiSession guiSession;
    private ItemStack targetItem;
    private AttemptPreview preview;
    private boolean processing;
    private boolean completed;
    private String operationId = "";
    private CompletionPhase completionPhase = CompletionPhase.OPEN;

    StrengthenGuiSession(Player player) {
        this.player = player;
        for (int index = 0; index < MATERIAL_SLOT_COUNT; index++) {
            materialInputs.add(null);
        }
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

    public ItemStack targetItem() {
        return targetItem;
    }

    public void setTargetItem(ItemStack targetItem) {
        this.targetItem = cloneNonAir(targetItem);
    }

    public ItemStack materialInput(int index) {
        return index >= 0 && index < materialInputs.size() ? materialInputs.get(index) : null;
    }

    public void setMaterialInput(int index, ItemStack itemStack) {
        if (index < 0 || index >= materialInputs.size()) {
            return;
        }
        materialInputs.set(index, cloneNonAir(itemStack));
    }

    public int firstEmptyMaterialSlot() {
        for (int index = 0; index < materialInputs.size(); index++) {
            if (materialInputs.get(index) == null) {
                return index;
            }
        }
        return -1;
    }

    public List<ItemStack> materialInputs() {
        List<ItemStack> copy = new ArrayList<>(materialInputs.size());
        for (ItemStack itemStack : materialInputs) {
            copy.add(cloneNonAir(itemStack));
        }
        return Collections.unmodifiableList(copy);
    }

    public AttemptPreview preview() {
        return preview;
    }

    public void setPreview(AttemptPreview preview) {
        this.preview = preview;
    }

    public boolean processing() {
        return processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
        if (processing) {
            completionPhase = CompletionPhase.PROCESSING;
        } else if (!completed && completionPhase == CompletionPhase.PROCESSING) {
            completionPhase = CompletionPhase.OPEN;
        }
    }

    public String operationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId == null ? "" : operationId.trim();
    }

    public CompletionPhase completionPhase() {
        return completionPhase;
    }

    public void advanceCompletionPhase(CompletionPhase phase) {
        if (phase != null && phase.ordinal() >= completionPhase.ordinal()) {
            completionPhase = phase;
        }
    }

    public boolean completed() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        if (completed) {
            completionPhase = CompletionPhase.COMPLETED;
        }
    }

    public AttemptContext toAttemptContext() {
        return AttemptContext.of(targetItem, materialInputs, operationId);
    }

    public void clearTargetItem() {
        targetItem = null;
    }

    public void clearStoredItems() {
        clearTargetItem();
        for (int index = 0; index < materialInputs.size(); index++) {
            materialInputs.set(index, null);
        }
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        return InventoryItemUtil.cloneNonAir(itemStack);
    }
}
