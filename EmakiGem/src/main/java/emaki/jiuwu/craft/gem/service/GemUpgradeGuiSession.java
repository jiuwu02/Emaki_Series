package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;

final class GemUpgradeGuiSession implements GemPlayerGuiSession {

    private final Player player;
    private GuiSession guiSession;
    private String currentTemplateId = "";
    private boolean templateSwitching;
    private ItemStack targetGem;
    private final Map<Integer, ItemStack> materialItems = new LinkedHashMap<>();

    GemUpgradeGuiSession(Player player) {
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

    public ItemStack targetGem() {
        return cloneNonAir(targetGem);
    }

    public ItemStack mutableTargetGem() {
        return targetGem;
    }

    public void setTargetGem(ItemStack targetGem) {
        this.targetGem = cloneNonAir(targetGem);
    }

    public ItemStack materialItem(int displayIndex) {
        return cloneNonAir(materialItems.get(displayIndex));
    }

    public Map<Integer, ItemStack> mutableMaterialItems() {
        return materialItems;
    }

    public void setMaterialItem(int displayIndex, ItemStack itemStack) {
        if (displayIndex < 0) {
            return;
        }
        ItemStack cloned = cloneNonAir(itemStack);
        if (cloned == null) {
            materialItems.remove(displayIndex);
            return;
        }
        materialItems.put(displayIndex, cloned);
    }

    public void clearMaterialItem(int displayIndex) {
        materialItems.remove(displayIndex);
    }

    public void clearMaterialItems() {
        materialItems.clear();
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        return InventoryItemUtil.cloneNonAir(itemStack);
    }
}
