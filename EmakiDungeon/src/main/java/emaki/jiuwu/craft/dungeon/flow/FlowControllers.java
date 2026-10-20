package emaki.jiuwu.craft.dungeon.flow;

import java.util.UUID;

public interface FlowControllers {

    boolean openGate(FlowRuntime runtime, String gateId, UUID playerId);

    boolean spawnWave(FlowRuntime runtime, String spawnId, int waveIndex, UUID playerId);

    void handleRoomEntered(FlowRuntime runtime, String roomId, UUID playerId);

    void triggerFail(FlowRuntime runtime, String reason);

    void giveTempItem(FlowRuntime runtime, String itemId, int amount);

    void handleOptionTrigger(FlowRuntime runtime, String optionId, UUID playerId);

    void handleMobDeath(FlowRuntime runtime, UUID entityId);

    void syncBossPoints(FlowRuntime runtime);
}
