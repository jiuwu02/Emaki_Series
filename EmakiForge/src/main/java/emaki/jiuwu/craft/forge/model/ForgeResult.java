package emaki.jiuwu.craft.forge.model;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

public final class ForgeResult {

    private boolean success;
    private String errorKey;
    private Map<String, Object> replacements = Map.of();
    private ItemStack resultItem;
    private String quality;
    private double multiplier = 1D;
    private String actionFailureReason;

    public boolean success() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String errorKey() {
        return errorKey;
    }

    public void setErrorKey(String errorKey) {
        this.errorKey = errorKey;
    }

    public Map<String, Object> replacements() {
        return replacements;
    }

    public void setReplacements(Map<String, Object> replacements) {
        this.replacements = replacements == null ? Map.of() : replacements;
    }

    public ItemStack resultItem() {
        return resultItem;
    }

    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem;
    }

    public String quality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public double multiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public String actionFailureReason() {
        return actionFailureReason;
    }

    public void setActionFailureReason(String actionFailureReason) {
        this.actionFailureReason = actionFailureReason;
    }
}
