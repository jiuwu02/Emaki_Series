package emaki.jiuwu.craft.dungeon.flow;

import java.util.UUID;

public interface FlowControllers {

    boolean openGate(FlowRuntime runtime, String gateId, UUID playerId);

    boolean spawnWave(FlowRuntime runtime, String spawnId, int waveIndex, UUID playerId);

    void handleRoomEntered(FlowRuntime runtime, String roomId, UUID playerId);

    void triggerFail(FlowRuntime runtime, String reason);

    void triggerComplete(FlowRuntime runtime, String cause);

    void triggerEnding(FlowRuntime runtime, String optionCase, String endingId);

    void giveTempItem(FlowRuntime runtime, String itemId, int amount);

    void handleRewardOpen(FlowRuntime runtime, String rewardId, UUID playerId);

    void handleOptionTrigger(FlowRuntime runtime, String optionId, UUID playerId);

    void handleMobDeath(FlowRuntime runtime, UUID entityId);

    void syncBossPoints(FlowRuntime runtime);
}
