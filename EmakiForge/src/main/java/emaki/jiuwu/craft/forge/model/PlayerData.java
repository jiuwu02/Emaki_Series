package emaki.jiuwu.craft.forge.model;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class PlayerData {

    public static final class ForgeHistory {

        private int craftCount;
        private String firstCraftedAt;
        private String lastCraftedAt;

        public ForgeHistory copy() {
            ForgeHistory copy = new ForgeHistory();
            copy.craftCount = craftCount;
            copy.firstCraftedAt = firstCraftedAt;
            copy.lastCraftedAt = lastCraftedAt;
            return copy;
        }

        public static ForgeHistory fromConfig(Object raw) {
            ForgeHistory history = new ForgeHistory();
            if (raw == null) {
                return history;
            }
            history.craftCount = Numbers.tryParseInt(ConfigNodes.get(raw, "craft_count"), 0);
            history.firstCraftedAt = ConfigNodes.string(raw, "first_crafted_at", null);
            history.lastCraftedAt = ConfigNodes.string(raw, "last_crafted_at", null);
            return history;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("craft_count", craftCount);
            map.put("first_crafted_at", firstCraftedAt);
            map.put("last_crafted_at", lastCraftedAt);
            return map;
        }

        public void increment(String timestamp) {
            craftCount++;
            if (firstCraftedAt == null) {
                firstCraftedAt = timestamp;
            }
            lastCraftedAt = timestamp;
        }

        public int craftCount() {
            return craftCount;
        }

        public boolean hasCrafted() {
            return craftCount > 0;
        }
    }

    private final String uuid;
    private final Map<String, ForgeHistory> recipeHistory = new LinkedHashMap<>();
    private final Map<String, Integer> guaranteeCounters = new LinkedHashMap<>();

    public PlayerData(String uuid) {
        this.uuid = uuid;
    }

    public PlayerData copy() {
        PlayerData copy = new PlayerData(uuid);
        for (Map.Entry<String, ForgeHistory> entry : recipeHistory.entrySet()) {
            copy.recipeHistory.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().copy());
        }
        copy.guaranteeCounters.putAll(guaranteeCounters);
        return copy;
    }

    public static PlayerData fromConfig(String uuid, YamlSection section) {
        PlayerData data = new PlayerData(uuid);
        if (section == null) {
            return data;
        }
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(section.get("recipes")).entrySet()) {
            data.recipeHistory.put(entry.getKey(), ForgeHistory.fromConfig(entry.getValue()));
        }
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(section.get("guarantee")).entrySet()) {
            data.guaranteeCounters.put(entry.getKey(), Numbers.tryParseInt(entry.getValue(), 0));
        }
        return data;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> recipes = new LinkedHashMap<>();
        for (Map.Entry<String, ForgeHistory> entry : recipeHistory.entrySet()) {
            recipes.put(entry.getKey(), entry.getValue().toMap());
        }
        result.put("uuid", uuid);
        result.put("recipes", recipes);
        result.put("guarantee", new LinkedHashMap<>(guaranteeCounters));
        return result;
    }

    public ForgeHistory history(String recipeId) {
        return recipeHistory.computeIfAbsent(recipeId, ignored -> new ForgeHistory());
    }

    public void recordCraft(String recipeId, String timestamp) {
        history(recipeId).increment(timestamp);
    }

    public boolean hasCrafted(String recipeId) {
        ForgeHistory history = recipeHistory.get(recipeId);
        return history != null && history.hasCrafted();
    }

    public int guaranteeCounter(String key) {
        return guaranteeCounters.getOrDefault(key, 0);
    }

    public void incrementGuaranteeCounter(String key) {
        guaranteeCounters.put(key, guaranteeCounter(key) + 1);
    }

    public void resetGuaranteeCounter(String key) {
        guaranteeCounters.put(key, 0);
    }
}
