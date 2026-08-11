package emaki.jiuwu.craft.forge.service;

import java.util.UUID;

import emaki.jiuwu.craft.forge.loader.PlayerDataStore;

/** Maps the public mastery query to Forge's persisted successful-craft history. */
final class ForgeMasteryService {

    private final PlayerDataStore playerDataStore;

    ForgeMasteryService(PlayerDataStore playerDataStore) {
        this.playerDataStore = playerDataStore;
    }

    int getMastery(UUID playerId, String recipeId) {
        if (playerDataStore == null || playerId == null || recipeId == null || recipeId.isBlank()) {
            return 0;
        }
        return playerDataStore.craftCount(playerId, recipeId);
    }
}
