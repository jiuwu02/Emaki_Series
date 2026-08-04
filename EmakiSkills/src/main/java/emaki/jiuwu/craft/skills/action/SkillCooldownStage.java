package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;

/**
 * Clears or sets one of the target's skill cooldowns.
 *
 * <p>Replaces the legacy {@code SkillCooldownAction}, which registered {@code skill_clearcooldown} and
 * {@code skill_setcooldown}. Both ids gain underscore separators to match the other modules' stage naming.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's cast timing state.</p>
 */
public final class SkillCooldownStage implements CoreActionStage {

    /** Ticks are stored as wall-clock millis, so a tick is 50ms. Matches v1's arithmetic exactly. */
    private static final long MILLIS_PER_TICK = 50L;

    /** Which cooldown mutation a stage instance performs. */
    public enum Operation {

        /** Remove one cooldown, or every timing entry when no skill is named. */
        CLEAR("skill_cooldown_clear", "Clears one or all of the target's skill cooldowns."),

        /** Write one skill's cooldown deadline. */
        SET("skill_cooldown_set", "Sets one of the target's skill cooldowns.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final EmakiSkillsPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the skill state service
     * @param operation which mutation this instance performs
     */
    public SkillCooldownStage(@NotNull EmakiSkillsPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "skills";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return switch (operation) {
            case SET -> List.of(
                    CoreStageParameter.required("skill", CoreStageParameterType.STRING, "Skill id"),
                    CoreStageParameter.required("duration_ticks", CoreStageParameterType.TIME,
                            "Cooldown duration in ticks; zero or less removes the cooldown"));
            case CLEAR -> List.of(
                    CoreStageParameter.optional("skill", CoreStageParameterType.STRING, "",
                            "Skill id; empty clears every cooldown and cast delay"));
        };
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
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        if (plugin.playerSkillStateService() == null || plugin.playerSkillDataStore() == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        // v1 loaded the profile purely as a liveness check before touching the timing state; the mutation
        // below goes through the data store, which resolves the profile itself.
        PlayerSkillProfile profile = plugin.playerSkillStateService().getProfile(target);
        if (profile == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        String skillId = Texts.normalizeId(arguments.getString("skill"));
        return operation == Operation.SET ? set(target, skillId, arguments) : clear(target, skillId);
    }

    private CoreActionOutcome set(Player target, String skillId, CoreResolvedArguments arguments) {
        if (plugin.playerSkillStateService().getDefinition(skillId) == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.skills.unknown_skill", Map.of("skill", skillId));
        }
        long ticks = arguments.getDurationTicks("duration_ticks", 0L);
        plugin.playerSkillDataStore().mutate(target, current -> {
            if (ticks <= 0L) {
                current.timingState().skillCooldownUntilBySkillId().remove(skillId);
            } else {
                current.timingState().skillCooldownUntilBySkillId().put(
                        skillId, System.currentTimeMillis() + ticks * MILLIS_PER_TICK);
            }
            current.markDirty();
        });
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("skill", skillId, "duration_ticks", ticks));
    }

    private CoreActionOutcome clear(Player target, String skillId) {
        boolean all = Texts.isBlank(skillId);
        plugin.playerSkillDataStore().mutate(target, current -> {
            if (all) {
                // clearAll also drops the global cooldown and the forced cast delay, which is what v1's
                // blank-skill branch did.
                current.timingState().clearAll();
            } else {
                current.timingState().skillCooldownUntilBySkillId().remove(skillId);
            }
            current.markDirty();
        });
        plugin.playerSkillDataStore().save(target);
        return all
                ? CoreActionOutcome.success(Map.of("all", true))
                : CoreActionOutcome.success(Map.of("all", false, "skill", skillId));
    }
}
