package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.text.Texts;

final class SteamerState {

    private long burningUntilMs;
    private int moisture;
    private int steam;
    private UUID playerUuid;
    private String playerName = "";
    private final Map<Integer, String> slotSources = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> slotItems = new LinkedHashMap<>();
    private final Map<Integer, Integer> slotProgress = new LinkedHashMap<>();

    long burningUntilMs() {
        return burningUntilMs;
    }

    void setBurningUntilMs(long burningUntilMs) {
        this.burningUntilMs = Math.max(0L, burningUntilMs);
    }

    int moisture() {
        return Math.max(0, moisture);
    }

    void setMoisture(int moisture) {
        this.moisture = Math.max(0, moisture);
    }

    int steam() {
        return Math.max(0, steam);
    }

    void setSteam(int steam) {
        this.steam = Math.max(0, steam);
    }

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

    boolean isCompletelyEmpty() {
        return burningUntilMs() <= 0L && moisture() <= 0 && steam() <= 0 && slotSources.isEmpty();
    }
}
