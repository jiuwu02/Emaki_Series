package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class ForgeGuiSession {

    private final Player player;
    private final String templateId;
    private Recipe recipe;
    private Recipe previewRecipe;
    private GuiSession guiSession;
    private final Map<Integer, ItemStack> blueprintItems = new LinkedHashMap<>();
    private ItemStack targetItem;
    private final Map<Integer, ItemStack> requiredMaterialItems = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> optionalMaterialItems = new LinkedHashMap<>();
    private int currentCapacity;
    private int maxCapacity;
    private boolean processing;
    private boolean forgeCompleted;
    private String previewFingerprint = "";
    private long previewSeed = ThreadLocalRandom.current().nextLong();
    private long previewForgedAt = System.currentTimeMillis();
    private ForgeService.PreparedForge preparedForge;

    public ForgeGuiSession(Player player, Recipe recipe, String templateId) {
        this.player = player;
        this.recipe = recipe;
        this.templateId = templateId;
    }

    public Player player() {
        return player;
    }

    public String templateId() {
        return templateId;
    }

    public Recipe recipe() {
        return recipe;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }

    public Recipe previewRecipe() {
        return previewRecipe;
    }

    public void setPreviewRecipe(Recipe previewRecipe) {
        this.previewRecipe = previewRecipe;
    }

    public GuiSession guiSession() {
        return guiSession;
    }

    public void setGuiSession(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    public Map<Integer, ItemStack> blueprintItems() {
        return blueprintItems;
    }

    public ItemStack targetItem() {
        return targetItem;
    }

    public void setTargetItem(ItemStack targetItem) {
        this.targetItem = targetItem;
    }

    public Map<Integer, ItemStack> requiredMaterialItems() {
        return requiredMaterialItems;
    }

    public Map<Integer, ItemStack> optionalMaterialItems() {
        return optionalMaterialItems;
    }

    public int currentCapacity() {
        return currentCapacity;
    }

    public void setCurrentCapacity(int currentCapacity) {
        this.currentCapacity = currentCapacity;
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public boolean processing() {
        return processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
    }

    public boolean forgeCompleted() {
        return forgeCompleted;
    }

    public void setForgeCompleted(boolean forgeCompleted) {
        this.forgeCompleted = forgeCompleted;
    }

    public String previewFingerprint() {
        return previewFingerprint;
    }

    public void setPreviewFingerprint(String previewFingerprint) {
        this.previewFingerprint = previewFingerprint == null ? "" : previewFingerprint;
    }

    public long previewSeed() {
        return previewSeed;
    }

    public long previewForgedAt() {
        return previewForgedAt;
    }

    public void refreshPreviewRoll() {
        this.previewSeed = ThreadLocalRandom.current().nextLong();
        this.previewForgedAt = System.currentTimeMillis();
    }

    public ForgeService.PreparedForge preparedForge() {
        return preparedForge;
    }

    public void setPreparedForge(ForgeService.PreparedForge preparedForge) {
        this.preparedForge = preparedForge;
    }

    public void clearStoredItems() {
        blueprintItems.clear();
        requiredMaterialItems.clear();
        optionalMaterialItems.clear();
        targetItem = null;
        preparedForge = null;
    }

    public GuiItems toGuiItems() {
        return new GuiItems(
                targetItem == null ? null : targetItem.clone(),
                copyItems(blueprintItems),
                copyItems(requiredMaterialItems),
                copyItems(optionalMaterialItems)
        );
    }

    private static Map<Integer, ItemStack> copyItems(Map<Integer, ItemStack> source) {
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : source.entrySet()) {
            ItemStack itemStack = ForgeGuiStateSupport.cloneNonAir(entry.getValue());
            if (itemStack != null) {
                result.put(entry.getKey(), itemStack);
            }
        }
        return result;
    }
}
