package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.text.Texts;

final class FermentationBarrelState {

    private long startedAtMs;
    private long finishAtMs;
    private boolean fermenting;
    private boolean completed;
    private UUID playerUuid;
    private String playerName = "";
    private String activeRecipeId = "";
    private final Map<Integer, String> slotSources = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> slotItems = new LinkedHashMap<>();
    private final Map<Integer, Integer> slotAmounts = new LinkedHashMap<>();

    long startedAtMs() { return startedAtMs; }
    void setStartedAtMs(long startedAtMs) { this.startedAtMs = Math.max(0L, startedAtMs); }
    long finishAtMs() { return finishAtMs; }
    void setFinishAtMs(long finishAtMs) { this.finishAtMs = Math.max(0L, finishAtMs); }
    boolean fermenting() { return fermenting; }
    void setFermenting(boolean fermenting) { this.fermenting = fermenting; }
    boolean completed() { return completed; }
    void setCompleted(boolean completed) { this.completed = completed; }
    UUID playerUuid() { return playerUuid; }
    String playerName() { return playerName; }
    String activeRecipeId() { return activeRecipeId; }
    void setActiveRecipeId(String activeRecipeId) { this.activeRecipeId = Texts.toStringSafe(activeRecipeId); }

    void setPlayerContext(UUID playerUuid, String playerName) {
        if (playerUuid != null) {
            this.playerUuid = playerUuid;
        }
        this.playerName = Texts.toStringSafe(playerName);
    }

    Map<Integer, String> slotSources() { return slotSources; }
    Map<Integer, Integer> slotAmounts() { return slotAmounts; }
    Map<String, Object> slotItemData(int slot) { return slotItems.get(slot); }

    void setSlot(int slot, String source, Map<String, Object> serializedItem, int amount) {
        if (slot < 0 || Texts.isBlank(source) || amount <= 0) {
            return;
        }
        slotSources.put(slot, source);
        slotAmounts.put(slot, Math.max(1, amount));
        if (serializedItem == null || serializedItem.isEmpty()) {
            slotItems.remove(slot);
        } else {
            slotItems.put(slot, Map.copyOf(serializedItem));
        }
    }

    void clearSlots() {
        slotSources.clear();
        slotItems.clear();
        slotAmounts.clear();
    }

    void clearProcess() {
        startedAtMs = 0L;
        finishAtMs = 0L;
        fermenting = false;
        completed = false;
        activeRecipeId = "";
    }

    boolean hasSlots() {
        return !slotSources.isEmpty();
    }

    boolean isCompletelyEmpty() {
        return !fermenting && !completed && slotSources.isEmpty();
    }
}
