package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.api.text.Texts;

final class FermentationBarrelState {

    private int schemaVersion = 2;
    private long startedAtMs;
    private long finishAtMs;
    private boolean fermenting;
    private boolean completed;
    private UUID playerUuid;
    private String playerName = "";
    private String activeRecipeId = "";
    private boolean valid = true;
    private boolean legacySlotIds;
    private boolean legacyCountKeys;
    private boolean slotIdMigrationFailed;
    private final Map<Integer, String> slotIds = new LinkedHashMap<>();
    private final Map<Integer, String> slotCountKeys = new LinkedHashMap<>();
    private final Map<Integer, String> slotSources = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object>> slotItems = new LinkedHashMap<>();
    private final Map<Integer, Integer> slotAmounts = new LinkedHashMap<>();

    int schemaVersion() { return schemaVersion; }
    void setSchemaVersion(int schemaVersion) { this.schemaVersion = Math.max(0, schemaVersion); }
    boolean needsSchemaWriteback() { return schemaVersion < 2; }
    void markSchemaCurrent() { this.schemaVersion = 2; }
    long startedAtMs() { return startedAtMs; }
    void setStartedAtMs(long startedAtMs) { this.startedAtMs = Math.max(0L, startedAtMs); }
    long finishAtMs() { return finishAtMs; }
    void setFinishAtMs(long finishAtMs) { this.finishAtMs = Math.max(0L, finishAtMs); }
    boolean fermenting() { return fermenting; }
    void setFermenting(boolean fermenting) { this.fermenting = fermenting; }
    boolean completed() { return completed; }
    void setCompleted(boolean completed) { this.completed = completed; }
    boolean valid() { return valid; }
    boolean slotIdsResolved() { return !legacySlotIds && !legacyCountKeys && !slotIdMigrationFailed; }
    boolean requiresIdentityMigration() { return legacySlotIds || legacyCountKeys; }
    boolean slotIdMigrationFailed() { return slotIdMigrationFailed; }
    void markInvalid() { this.valid = false; }
    void markLegacySlotIds() { this.legacySlotIds = true; }
    void markLegacyCountKeys() { this.legacyCountKeys = true; }
    void markSlotIdsResolved() { this.legacySlotIds = false; this.legacyCountKeys = false; this.slotIdMigrationFailed = false; }
    void markSlotIdMigrationFailed() { this.slotIdMigrationFailed = true; }
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

    Map<Integer, String> slotIds() { return slotIds; }
    Map<Integer, String> slotCountKeys() { return slotCountKeys; }
    Map<Integer, String> slotSources() { return slotSources; }
    Map<Integer, Integer> slotAmounts() { return slotAmounts; }
    Map<String, Object> slotItemData(int slot) { return slotItems.get(slot); }

    void setSlot(int slot, String slotId, String countKey, String source, Map<String, Object> serializedItem, int amount) {
        if (slot < 0 || Texts.isBlank(slotId) || Texts.isBlank(countKey) || Texts.isBlank(source) || amount <= 0) {
            return;
        }
        slotIds.put(slot, Texts.toStringSafe(slotId).trim().toLowerCase(Locale.ROOT));
        slotCountKeys.put(slot, Texts.toStringSafe(countKey).trim().toLowerCase(Locale.ROOT));
        slotSources.put(slot, source);
        slotAmounts.put(slot, Math.max(1, amount));
        if (serializedItem == null || serializedItem.isEmpty()) {
            slotItems.remove(slot);
        } else {
            slotItems.put(slot, Map.copyOf(serializedItem));
        }
    }

    void replaceSlotIdentity(int slot, String slotId, String countKey) {
        if (!slotIds.containsKey(slot) || Texts.isBlank(slotId) || Texts.isBlank(countKey)) {
            return;
        }
        slotIds.put(slot, Texts.toStringSafe(slotId).trim().toLowerCase(Locale.ROOT));
        slotCountKeys.put(slot, Texts.toStringSafe(countKey).trim().toLowerCase(Locale.ROOT));
    }

    void clearSlots() {
        slotIds.clear();
        slotCountKeys.clear();
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
