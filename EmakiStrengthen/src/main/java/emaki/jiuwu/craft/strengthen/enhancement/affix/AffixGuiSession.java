package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptPreview;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;

/**
 * 词条强化 GUI 的会话状态。
 *
 * <p>刻意不复用整件星级强化的 {@code StrengthenGuiSession}：后者保存 {@code AttemptPreview} 与星级
 * 结算阶段，词条流程保存的是通用强化预览与选中词条，两者的字段含义不同，混用会让任一侧的语义
 * 被另一侧牵着改。
 *
 * <p><strong>线程：</strong>仅在玩家所属实体线程读写。
 */
final class AffixGuiSession {

    private static final int MATERIAL_SLOT_COUNT = 4;

    private final Player player;
    private final EnhancementRecipe recipe;
    private final List<ItemStack> materialInputs = new ArrayList<>(MATERIAL_SLOT_COUNT);
    private GuiSession guiSession;
    private ItemStack targetItem;
    private EnhancementAttemptPreview preview;
    private List<String> candidates = List.of();
    private String selectedAffix = "";
    private int capacityUsed;
    private int capacityMax;
    private boolean processing;

    AffixGuiSession(Player player, EnhancementRecipe recipe) {
        this.player = player;
        this.recipe = recipe;
        for (int index = 0; index < MATERIAL_SLOT_COUNT; index++) {
            materialInputs.add(null);
        }
    }

    Player player() {
        return player;
    }

    EnhancementRecipe recipe() {
        return recipe;
    }

    GuiSession guiSession() {
        return guiSession;
    }

    void setGuiSession(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    ItemStack targetItem() {
        return targetItem;
    }

    void setTargetItem(ItemStack targetItem) {
        this.targetItem = cloneNonAir(targetItem);
    }

    ItemStack materialInput(int index) {
        return index >= 0 && index < materialInputs.size() ? materialInputs.get(index) : null;
    }

    void setMaterialInput(int index, ItemStack itemStack) {
        if (index >= 0 && index < materialInputs.size()) {
            materialInputs.set(index, cloneNonAir(itemStack));
        }
    }

    int firstEmptyMaterialSlot() {
        for (int index = 0; index < materialInputs.size(); index++) {
            if (materialInputs.get(index) == null) {
                return index;
            }
        }
        return -1;
    }

    List<ItemStack> materialInputs() {
        List<ItemStack> copy = new ArrayList<>(materialInputs.size());
        for (ItemStack itemStack : materialInputs) {
            copy.add(cloneNonAir(itemStack));
        }
        return Collections.unmodifiableList(copy);
    }

    List<ItemStack> suppliedMaterials() {
        List<ItemStack> supplied = new ArrayList<>(materialInputs.size());
        for (ItemStack itemStack : materialInputs) {
            if (itemStack != null) {
                supplied.add(itemStack);
            }
        }
        return supplied;
    }

    EnhancementAttemptPreview preview() {
        return preview;
    }

    void setPreview(EnhancementAttemptPreview preview) {
        this.preview = preview;
    }

    List<String> candidates() {
        return candidates;
    }

    void setCandidates(List<String> candidates) {
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    String selectedAffix() {
        return selectedAffix;
    }

    void setSelectedAffix(String selectedAffix) {
        this.selectedAffix = selectedAffix == null ? "" : selectedAffix;
    }

    int capacityUsed() {
        return capacityUsed;
    }

    int capacityMax() {
        return capacityMax;
    }

    void setCapacity(int used, int max) {
        this.capacityUsed = Math.max(0, used);
        this.capacityMax = Math.max(0, max);
    }

    boolean processing() {
        return processing;
    }

    void setProcessing(boolean processing) {
        this.processing = processing;
    }

    void clearStoredItems() {
        targetItem = null;
        for (int index = 0; index < materialInputs.size(); index++) {
            materialInputs.set(index, null);
        }
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        return InventoryItemUtil.cloneNonAir(itemStack);
    }
}
