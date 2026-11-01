package emaki.jiuwu.craft.dungeon.flow;

import java.util.List;
import java.util.Map;

public final class FlowModules {

    public record TriggerLimits(int perTrigger, int perPlayer, int total) {

        public static TriggerLimits unlimited() {
            return new TriggerLimits(0, 0, 0);
        }
    }

    public record Head(String id, boolean enabled, TriggerLimits limits) {
    }

    public record SpawnPointDef(Head head, FlowPoint point) {
    }

    public record CheckpointDef(Head head, String recordId, FlowRegion region, FlowPoint point, int minPlayers) {
    }

    public record RespawnPointDef(Head head, FlowPoint point, String checkpointId) {
    }

    public record ExitPointDef(Head head, FlowPoint point, boolean needConfirm) {
    }

    public record GateDef(Head head,
            String gateId,
            List<FlowRegion> doorBlocks,
            List<ConditionDef> conditions,
            String logic,
            String openMode,
            FlowPoint redstonePoint) {
    }

    public record MobSpawnRef(String mobId, int count, int weight) {
    }

    public record WaveDef(int intervalTicks, boolean dropLoot, List<MobSpawnRef> mobs) {
    }

    public record SpawnPointModuleDef(Head head,
            String spawnId,
            FlowPoint point,
            int range,
            int minPlayers,
            boolean once,
            List<WaveDef> waves) {
    }

    public record TriggerZoneDef(Head head, FlowRegion region, String direction, List<ConditionDef> conditions) {
    }

    public record BlockInteractionDef(Head head,
            FlowPoint point,
            String click,
            List<ConditionDef> conditions,
            List<FlowActionDef> actions) {
    }

    public record RewardDef(Head head, FlowPoint point, String lootTable, List<ConditionDef> conditions) {
    }

    public record BossPointDef(Head head, FlowPoint point, String source, String bossId, List<ConditionDef> conditions) {
    }

    public record OptionCase(String label, String caseId, String endingId, List<FlowRegion> sealRegions) {
    }

    public record OptionDef(Head head,
            String optionId,
            FlowPoint point,
            String selectMode,
            List<OptionCase> cases,
            int timeLimitSeconds,
            int minSelect,
            int maxSelect,
            List<ConditionDef> conditions,
            FlowPoint safePoint,
            Map<String, String> endingMap) {
    }

    public record HintDef(Head head, FlowPoint point, FlowRegion region, String text, String channel, List<ConditionDef> conditions) {
    }

    public record TeleportZoneDef(Head head, FlowRegion region, FlowPoint target, List<ConditionDef> conditions, boolean allMembers) {
    }

    public record BoundaryZoneDef(Head head, FlowRegion region, FlowPoint pullback) {
    }

    public record EffectZoneDef(Head head, FlowRegion region, List<String> effects, int intervalTicks) {
    }

    public record BlockStateTarget(FlowPoint point, String blockData) {
    }

    public record MechanismDef(Head head, List<BlockStateTarget> targets, List<ConditionDef> conditions) {
    }

    public record RoomTimerDef(Head head, FlowRegion region, int seconds, FlowActionDef onZero) {
    }

    public record KillTargetDef(Head head, String target, int count, FlowRegion scope, List<FlowActionDef> actions) {
    }

    public record ItemInteractionDef(Head head,
            FlowPoint point,
            String itemId,
            int amount,
            boolean consume,
            List<ConditionDef> conditions,
            List<FlowActionDef> actions) {
    }

    public record FxDef(Head head, FlowPoint point, FlowRegion region, String sound, String particle, double radius, List<ConditionDef> conditions) {
    }

    public record TempItemDef(Head head, String itemId, int amount, String timing, String hint) {
    }

    public record RoomDef(String id, int order, FlowRegion region, FlowPoint arrival) {
    }

    private FlowModules() {
    }
}
