package emaki.jiuwu.craft.skills.action.v2;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;

/**
 * Casts a MythicMobs skill with the target as the caster.
 *
 * <p>The v2 counterpart of {@code CastSkillAction}. v1 registered the same action twice, under
 * {@code castskill} and {@code skill.cast}; only one id remains because v2 stage ids are single-word and
 * a dotted alias would read as a nested key in pipeline text.</p>
 *
 * <p>v1 declared this action async and returned its result through {@code executeAsync}. That was never a
 * real off-thread path: the body ran the Mythic cast inline on the calling thread. Domain
 * {@code CONTEXT_ENTITY} states what actually happens, that a skill is cast by one entity in its own
 * region, which is the only domain Folia will accept for this work.</p>
 */
public final class CastSkillStage implements CoreActionStage {

    private final EmakiSkillsPlugin plugin;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the Mythic cast service
     */
    public CastSkillStage(@NotNull EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return "cast_skill";
    }

    @Override
    public @NotNull String description() {
        return "Casts a MythicMobs skill with the target as the caster.";
    }

    @Override
    public @NotNull String category() {
        return "skills";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(CoreStageParameter.required("skill", CoreStageParameterType.STRING,
                "MythicMobs skill id to cast"));
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
        Player target = SkillsStageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        MythicSkillCastService castService = plugin.mythicSkillCastService();
        if (castService == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        // Trimmed rather than normalized: Mythic skill ids are case-sensitive and are not this module's
        // own id space, so v1's raw read is preserved.
        String skillId = Texts.trim(arguments.getString("skill"));
        if (skillId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.skills.skill_required");
        }
        if (!castService.isAvailable()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.v2.stage.skills.mythic_unavailable");
        }
        if (!castService.skillExists(skillId)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.skills.unknown_mythic_skill", Map.of("skill", skillId));
        }
        return castService.cast(target, skillId)
                ? CoreActionOutcome.success(Map.of("skill", skillId))
                : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                        "action.v2.stage.skills.cast_failed", Map.of("skill", skillId));
    }
}
