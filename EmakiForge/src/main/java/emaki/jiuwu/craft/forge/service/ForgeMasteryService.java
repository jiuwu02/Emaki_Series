package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ForgeMasteryService {

    private static final double DEFAULT_MASTERY_BONUS_PER_LEVEL = 0.01D;
    private static final int DEFAULT_MAX_MASTERY = 100;

    private final Map<UUID, Map<String, Integer>> masteryData = new ConcurrentHashMap<>();
    private double bonusPerLevel = DEFAULT_MASTERY_BONUS_PER_LEVEL;
    private int maxMastery = DEFAULT_MAX_MASTERY;

    ForgeMasteryService() {
    }

    void recordCraft(UUID playerId, String recipeId) {
        if (playerId == null || recipeId == null || recipeId.isBlank()) {
            return;
        }
        masteryData.computeIfAbsent(playerId, _ -> new ConcurrentHashMap<>())
                .merge(recipeId, 1, (current, _) -> Math.min(current + 1, maxMastery));
    }

    int getMastery(UUID playerId, String recipeId) {
        if (playerId == null || recipeId == null) {
            return 0;
        }
        Map<String, Integer> playerData = masteryData.get(playerId);
        if (playerData == null) {
            return 0;
        }
        return playerData.getOrDefault(recipeId, 0);
    }

    double qualityBonusMultiplier(UUID playerId, String recipeId) {
        int mastery = getMastery(playerId, recipeId);
        if (mastery <= 0) {
            return 1.0D;
        }
        return 1.0D + mastery * bonusPerLevel;
    }

    void clearPlayer(UUID playerId) {
        if (playerId != null) {
            masteryData.remove(playerId);
        }
    }

    void loadPlayerData(UUID playerId, Map<String, Integer> data) {
        if (playerId == null || data == null || data.isEmpty()) {
            return;
        }
        masteryData.put(playerId, new ConcurrentHashMap<>(data));
    }

    Map<String, Integer> getPlayerData(UUID playerId) {
        if (playerId == null) {
            return Map.of();
        }
        Map<String, Integer> data = masteryData.get(playerId);
        return data == null ? Map.of() : Map.copyOf(data);
    }

    void configure(double bonusPerLevel, int maxMastery) {
        this.bonusPerLevel = Math.max(0D, bonusPerLevel);
        this.maxMastery = Math.max(1, maxMastery);
    }
}
