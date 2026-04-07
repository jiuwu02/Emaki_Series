package emaki.jiuwu.craft.strengthen.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;

final class StrengthenGuiSession {

    private final Player player;
    private GuiSession guiSession;
    private ItemStack targetItem;
    private ItemStack baseMaterial;
    private ItemStack supportMaterial;
    private ItemStack protectionMaterial;
    private ItemStack breakthroughMaterial;
    private AttemptPreview preview;
    private boolean processing;
    private boolean completed;

    StrengthenGuiSession(Player player) {
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

    public ItemStack targetItem() {
        return targetItem;
    }

    public void setTargetItem(ItemStack targetItem) {
        this.targetItem = cloneNonAir(targetItem);
    }

    public ItemStack baseMaterial() {
        return baseMaterial;
    }

    public void setBaseMaterial(ItemStack baseMaterial) {
        this.baseMaterial = cloneNonAir(baseMaterial);
    }

    public ItemStack supportMaterial() {
        return supportMaterial;
    }

    public void setSupportMaterial(ItemStack supportMaterial) {
        this.supportMaterial = cloneNonAir(supportMaterial);
    }

    public ItemStack protectionMaterial() {
        return protectionMaterial;
    }

    public void setProtectionMaterial(ItemStack protectionMaterial) {
        this.protectionMaterial = cloneNonAir(protectionMaterial);
    }

    public ItemStack breakthroughMaterial() {
        return breakthroughMaterial;
    }

    public void setBreakthroughMaterial(ItemStack breakthroughMaterial) {
        this.breakthroughMaterial = cloneNonAir(breakthroughMaterial);
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
    }

    public boolean completed() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public AttemptContext toAttemptContext() {
        return AttemptContext.of(targetItem, baseMaterial, supportMaterial, protectionMaterial, breakthroughMaterial);
    }

    public void clearStoredItems() {
        targetItem = null;
        baseMaterial = null;
        supportMaterial = null;
        protectionMaterial = null;
        breakthroughMaterial = null;
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return itemStack.clone();
    }
}
