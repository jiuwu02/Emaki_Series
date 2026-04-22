package emaki.jiuwu.craft.gem.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;

final class GemOpenGuiSession implements GemPlayerGuiSession {

    private final Player player;
    private GuiSession guiSession;
    private String currentTemplateId = "";
    private boolean templateSwitching;
    private ItemStack targetItem;
    private ItemStack openerItem;
    private int selectedSlotIndex = -1;

    GemOpenGuiSession(Player player) {
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

    public ItemStack targetItem() {
        return cloneNonAir(targetItem);
    }

    public ItemStack mutableTargetItem() {
        return targetItem;
    }

    public void setTargetItem(ItemStack targetItem) {
        this.targetItem = cloneNonAir(targetItem);
        this.selectedSlotIndex = -1;
    }

    public ItemStack openerItem() {
        return cloneNonAir(openerItem);
    }

    public ItemStack mutableOpenerItem() {
        return openerItem;
    }

    public void setOpenerItem(ItemStack openerItem) {
        this.openerItem = cloneNonAir(openerItem);
        this.selectedSlotIndex = -1;
    }

    public int selectedSlotIndex() {
        return selectedSlotIndex;
    }

    public void setSelectedSlotIndex(int selectedSlotIndex) {
        this.selectedSlotIndex = selectedSlotIndex;
    }

    public void clearSelectedSlot() {
        this.selectedSlotIndex = -1;
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        return InventoryItemUtil.cloneNonAir(itemStack);
    }
}
