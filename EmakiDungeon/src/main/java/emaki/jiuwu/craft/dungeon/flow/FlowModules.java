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

    public record TriggerSpec(String actor, String event, int range, int minPlayers, boolean once) {

        public static TriggerSpec defaults() {
            return new TriggerSpec("player", "enter", 8, 1, true);
        }
    }

    public record SpawnPointDef(Head head, FlowPoint point, List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record CheckpointDef(Head head, String recordId, FlowRegion region, FlowPoint point, int minPlayers,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record RespawnPointDef(Head head, FlowPoint point, String checkpointId,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record ExitPointDef(Head head, FlowPoint point, boolean needConfirm,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record GateDef(Head head,
            String gateId,
            List<FlowRegion> doorBlocks,
            List<ConditionDef> conditions,
            String logic,
            String openMode,
            FlowPoint redstonePoint,
            TriggerSpec trigger) {
    }

    public record MobSpawnRef(String mobId, int count, int weight, String source, boolean boss) {
    }

    public record WaveDef(int intervalTicks, boolean dropLoot, List<MobSpawnRef> mobs) {
    }

    public record SpawnPointModuleDef(Head head,
            String spawnId,
            FlowPoint point,
            List<WaveDef> waves,
            List<ConditionDef> conditions,
            TriggerSpec trigger) {
    }

    public record TriggerZoneDef(Head head, FlowRegion region, String direction,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record BlockInteractionDef(Head head,
            FlowPoint point,
            String click,
            List<ConditionDef> conditions,
            List<FlowActionDef> actions,
            TriggerSpec trigger) {
    }

    public record RewardDef(Head head, FlowPoint point, String lootTable,
            List<ConditionDef> conditions, TriggerSpec trigger) {
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
            Map<String, String> endingMap,
            TriggerSpec trigger) {
    }

    public record HintDef(Head head, FlowPoint point, FlowRegion region, String text, String type,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record TeleportZoneDef(Head head, FlowRegion region, FlowPoint target,
            List<ConditionDef> conditions, boolean allMembers, TriggerSpec trigger) {
    }

    public record BoundaryZoneDef(Head head, FlowRegion region, FlowPoint pullback,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record EffectZoneDef(Head head, FlowRegion region, List<String> effects, int intervalTicks,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record BlockStateTarget(FlowPoint point, String blockData) {
    }

    public record MechanismDef(Head head, List<BlockStateTarget> targets,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record RoomTimerDef(Head head, FlowRegion region, int seconds, FlowActionDef onZero,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record KillTargetDef(Head head, String target, int count, FlowRegion scope, List<FlowActionDef> actions,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record ItemInteractionDef(Head head,
            FlowPoint point,
            String itemId,
            int amount,
            boolean consume,
            List<ConditionDef> conditions,
            List<FlowActionDef> actions,
            TriggerSpec trigger) {
    }

    public record FxDef(Head head, FlowPoint point, FlowRegion region, String sound, String particle,
            double radius, List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record TempItemDef(Head head, String itemId, int amount, String timing, String hint,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    public record RoomDef(String id, int order, FlowRegion region, FlowPoint arrival,
            List<ConditionDef> conditions, TriggerSpec trigger) {
    }

    private FlowModules() {
    }
}
