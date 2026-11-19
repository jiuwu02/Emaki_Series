package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.bridge.MythicBridge;

public final class CastMythicSkillStage implements CoreActionStage {

    private final EmakiSkillsPlugin plugin;

    public CastMythicSkillStage(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return "cast_mythic_skill";
    }

    @Override
    public @NotNull String description() {
        return "对目标释放一个 MythicMobs 技能.";
    }

    @Override
    public @NotNull String category() {
        return "integration";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(CoreStageParameter.required("skill", CoreStageParameterType.STRING,
                "MythicMobs 技能 ID"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = entity(context.currentTarget());
        if (target == null) {
            target = entity(context.caster());
        }
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        String skillId = Texts.trim(arguments.getString("skill"));
        if (skillId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.cast_mythic_skill.skill_required");
        }
        MythicBridge bridge = plugin.mythicBridge();
        if (bridge == null || !bridge.isAvailable()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.stage.cast_mythic_skill.unavailable");
        }
        Entity caster = entity(context.caster());
        if (caster == null) {
            caster = target;
        }
        Location origin = target.getLocation();
        debug(caster.getUniqueId(), "cast.mythic_attempt", Map.of(
                "skill", skillId, "caster", caster.getName(), "target", target.getName()));
        MythicBridge.CastResult result = bridge.castSkillDetailed(caster, skillId, target, origin,
                List.of(target), List.of(origin), Map.of());
        debug(caster.getUniqueId(), "cast.mythic_result", Map.of(
                "skill", skillId,
                "status", result.status().name(),
                "detail", Texts.toStringSafe(result.detail())));
        return switch (result.status()) {
            case SUCCESS -> CoreActionOutcome.success(Map.of("skill", skillId));
            case SKILL_NOT_FOUND -> CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "skill.mythic_not_found", Map.of("mythic_skill", skillId));
            case CONDITIONS_UNMET -> CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.cast_mythic_skill.conditions_unmet",
                    Map.of("skill", skillId, "mythic_skill", skillId,
                            "detail", Texts.toStringSafe(result.detail())));
            case UNAVAILABLE -> CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.stage.cast_mythic_skill.unavailable");
            case ERROR -> CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.cast_mythic_skill.cast_error",
                    Map.of("skill", skillId, "error", Texts.toStringSafe(result.detail())));
        };
    }

    private void debug(java.util.UUID player, String langKey, Map<String, ?> replacements) {
        var logger = plugin.debugLogger();
        if (logger != null) {
            logger.log("cast", player, langKey, replacements);
        }
    }

    private static Entity entity(CoreActionSubject subject) {
        return subject == null ? null : subject.entityOrNull();
    }
}
