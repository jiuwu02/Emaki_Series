package emaki.jiuwu.craft.level.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EmakiLevelApi {

    Optional<LevelTypeView> type(String typeId);

    Collection<LevelTypeView> types();

    CompletableFuture<PlayerLevelView> getPlayerData(UUID uuid);

    int getLevel(UUID uuid, String typeId);

    double getExp(UUID uuid, String typeId);

    double getTotalExp(UUID uuid, String typeId);

    double getRequiredExp(UUID uuid, String typeId, int targetLevel);

    LevelOperationResult addExp(UUID uuid, String typeId, double amount, String reason);

    LevelOperationResult removeExp(UUID uuid, String typeId, double amount, String reason);

    LevelOperationResult setExp(UUID uuid, String typeId, double amount, String reason);

    LevelOperationResult addLevel(UUID uuid, String typeId, int amount, String reason);

    LevelOperationResult removeLevel(UUID uuid, String typeId, int amount, String reason);

    LevelOperationResult setLevel(UUID uuid, String typeId, int level, String reason);

    LevelOperationResult levelUp(UUID uuid, String typeId, LevelUpCause cause);
}
