package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;

final class ItemRepairGuiSession {

    private static final int MATERIAL_SLOT_COUNT = 4;

    private final Player player;
    private final List<ItemStack> materialInputs = new ArrayList<>(MATERIAL_SLOT_COUNT);
    private GuiSession guiSession;
    private ItemStack targetItem;
    private boolean processing;
    private boolean completed;

    ItemRepairGuiSession(Player player) {
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

    public List<ItemStack> materialInputs() {
        List<ItemStack> result = new ArrayList<>(materialInputs.size());
        for (ItemStack itemStack : materialInputs) {
            result.add(cloneNonAir(itemStack));
        }
        return result;
    }

    public Map<Integer, ItemStack> materialInputMap() {
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        for (int index = 0; index < materialInputs.size(); index++) {
            ItemStack itemStack = cloneNonAir(materialInputs.get(index));
            if (itemStack != null) {
                result.put(index, itemStack);
            }
        }
        return result;
    }

    public int firstEmptyMaterialSlot() {
        for (int index = 0; index < materialInputs.size(); index++) {
            if (materialInputs.get(index) == null) {
                return index;
            }
        }
        return -1;
    }

    public boolean processing() {
        return processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
    }

    public boolean completed() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void clearStoredItems() {
        targetItem = null;
        for (int index = 0; index < materialInputs.size(); index++) {
            materialInputs.set(index, null);
        }
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        return InventoryItemUtil.cloneNonAir(itemStack);
    }
}
