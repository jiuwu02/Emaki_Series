package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.text.Texts;

final class JuicerState {

    private UUID playerUuid;
    private String playerName = "";
    private final Map<Integer, String> slotSources = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> slotItems = new LinkedHashMap<>();
    private final Map<Integer, Integer> slotProgress = new LinkedHashMap<>();
    private String fluidId = "";
    private String fluidDisplayName = "";
    private int fluidAmountMl;

    UUID playerUuid() {
        return playerUuid;
    }

    String playerName() {
        return playerName;
    }

    void setPlayerContext(UUID playerUuid, String playerName) {
        if (playerUuid != null) {
            this.playerUuid = playerUuid;
        }
        this.playerName = Texts.toStringSafe(playerName);
    }

    Map<Integer, String> slotSources() {
        return slotSources;
    }

    Map<Integer, Integer> slotProgress() {
        return slotProgress;
    }

    Map<String, Object> slotItemData(int slot) {
        return slotItems.get(slot);
    }

    int progressAt(int slot) {
        return Math.max(0, slotProgress.getOrDefault(slot, 0));
    }

    void setProgress(int slot, int progress) {
        if (slot < 0) {
            return;
        }
        int normalized = Math.max(0, progress);
        if (normalized <= 0) {
            slotProgress.remove(slot);
            return;
        }
        slotProgress.put(slot, normalized);
    }

    void setSlotSource(int slot, String source) {
        if (slot < 0 || Texts.isBlank(source)) {
            return;
        }
        slotSources.put(slot, source);
    }

    void setSlotItem(int slot, Map<String, Object> serializedItem) {
        if (slot < 0) {
            return;
        }
        if (serializedItem == null || serializedItem.isEmpty()) {
            slotItems.remove(slot);
            return;
        }
        slotItems.put(slot, Map.copyOf(serializedItem));
    }

    void removeSlot(int slot) {
        slotSources.remove(slot);
        slotItems.remove(slot);
        slotProgress.remove(slot);
    }

    void clearSlots() {
        slotSources.clear();
        slotItems.clear();
        slotProgress.clear();
    }

    String fluidId() {
        return fluidId;
    }

    String fluidDisplayName() {
        return Texts.isBlank(fluidDisplayName) ? fluidId : fluidDisplayName;
    }

    int fluidAmountMl() {
        return Math.max(0, fluidAmountMl);
    }

    boolean hasFluid() {
        return Texts.isNotBlank(fluidId) && fluidAmountMl() > 0;
    }

    void setFluid(String fluidId, String fluidDisplayName, int fluidAmountMl) {
        if (Texts.isBlank(fluidId) || fluidAmountMl <= 0) {
            clearFluid();
            return;
        }
        this.fluidId = Texts.toStringSafe(fluidId).trim();
        this.fluidDisplayName = Texts.toStringSafe(fluidDisplayName).trim();
        this.fluidAmountMl = Math.max(0, fluidAmountMl);
    }

    boolean canAcceptFluid(String fluidId, int addAmountMl, int maxAmountMl) {
        if (Texts.isBlank(fluidId) || addAmountMl <= 0) {
            return false;
        }
        if (hasFluid() && !this.fluidId.equalsIgnoreCase(fluidId)) {
            return false;
        }
        return fluidAmountMl() + addAmountMl <= Math.max(1, maxAmountMl);
    }

    void addFluid(String fluidId, String fluidDisplayName, int amountMl, int maxAmountMl) {
        if (!canAcceptFluid(fluidId, amountMl, maxAmountMl)) {
            return;
        }
        setFluid(fluidId, Texts.isBlank(fluidDisplayName) ? fluidId : fluidDisplayName, fluidAmountMl() + amountMl);
    }

    boolean consumeFluid(int amountMl) {
        int normalized = Math.max(1, amountMl);
        if (fluidAmountMl() < normalized) {
            return false;
        }
        fluidAmountMl -= normalized;
        if (fluidAmountMl <= 0) {
            clearFluid();
        }
        return true;
    }

    void clearFluid() {
        fluidId = "";
        fluidDisplayName = "";
        fluidAmountMl = 0;
    }

    boolean isCompletelyEmpty() {
        return slotSources.isEmpty() && !hasFluid();
    }
}
