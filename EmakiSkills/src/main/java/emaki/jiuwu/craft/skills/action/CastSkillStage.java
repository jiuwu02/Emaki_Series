package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
import emaki.jiuwu.craft.skills.model.CastAttemptResult;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.CastAttemptService.CastBypass;

public final class CastSkillStage implements CoreActionStage {

    private final EmakiSkillsPlugin plugin;

    public CastSkillStage(@NotNull EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return "cast_skill";
    }

    @Override
    public @NotNull String description() {
        return "Casts an EmakiSkills skill with the target as the caster.";
    }

    @Override
    public @NotNull String category() {
        return "skills";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(
                CoreStageParameter.required("skill", CoreStageParameterType.STRING,
                        "EmakiSkills skill id"),
                CoreStageParameter.optional("bypass_cooldown", CoreStageParameterType.BOOLEAN, "false",
                        "Skips the per-skill and global cooldown checks; ignored for non-player casters"),
                CoreStageParameter.optional("bypass_resource_check", CoreStageParameterType.BOOLEAN, "false",
                        "Casts even when the caster cannot afford it; ignored for non-player casters"),
                CoreStageParameter.optional("consume_resource", CoreStageParameterType.BOOLEAN, "true",
                        "Whether a successful cast bills its resource costs; ignored for non-player casters"));
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
        Entity caster = entity(context.currentTarget());
        if (caster == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }

        String skillId = Texts.normalizeId(arguments.getString("skill"));
        if (skillId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.skills.skill_required");
        }
        if (plugin.playerSkillStateService() == null || plugin.castAttemptService() == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        SkillDefinition definition = plugin.playerSkillStateService().getDefinition(skillId);
        if (definition == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.skills.unknown_skill", Map.of("skill", skillId));
        }
        return caster instanceof Player player
                ? castAsPlayer(player, definition, skillId, arguments)
                : castAsEntity(caster, definition, skillId);
    }

    private CoreActionOutcome castAsPlayer(Player player,
            SkillDefinition definition,
            String skillId,
            CoreResolvedArguments arguments) {
        CastBypass bypass = new CastBypass(
                arguments.getBoolean("bypass_cooldown", false),
                arguments.getBoolean("bypass_resource_check", false),
                arguments.getBoolean("consume_resource", true));
        CastAttemptService castService = plugin.castAttemptService();
        CompletableFuture<CastAttemptResult> attempt =
                castService.attemptDirectCast(player, "pipeline", definition, null, bypass);

        CastAttemptResult settled = attempt.getNow(null);
        if (settled != null && !settled.success()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.skills.cast_failed", Map.of("skill", skillId));
        }
        attempt.whenComplete((result, throwable) -> logLateFailure(skillId, result, throwable));
        return CoreActionOutcome.success(Map.of("skill", skillId));
    }

    private CoreActionOutcome castAsEntity(Entity caster, SkillDefinition definition, String skillId) {
        if (Texts.isBlank(definition.mythicSkill())) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.skills.entity_caster_needs_mythic", Map.of("skill", skillId));
        }
        return plugin.castAttemptService().castAsEntity(caster, definition)
                ? CoreActionOutcome.success(Map.of("skill", skillId))
                : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                        "action.stage.skills.cast_failed", Map.of("skill", skillId));
    }

    private void logLateFailure(String skillId, CastAttemptResult result, Throwable throwable) {
        if (throwable == null && (result == null || result.success())) {
            return;
        }
        plugin.getLogger().log(Level.FINE, "cast_skill: skill '" + skillId
                + "' did not complete: " + (throwable != null
                        ? Texts.toStringSafe(throwable.getMessage())
                        : Texts.toStringSafe(result.failureMessage())));
    }

    private static Entity entity(CoreActionSubject subject) {
        return subject == null ? null : subject.entityOrNull();
    }
}
