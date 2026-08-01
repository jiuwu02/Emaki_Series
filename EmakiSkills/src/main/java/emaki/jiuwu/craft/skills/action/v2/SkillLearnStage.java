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

/**
 * Unlocks or removes manually learned skills for the target.
 *
 * <p>The v2 counterpart of {@code SkillLearnAction}. All three v1 ids ({@code skill_learn},
 * {@code skill_forget}, {@code skill_forget_all}) are kept verbatim.</p>
 *
 * <p>Only the manual skill source is touched. Skills granted by equipment or other sources are collected
 * elsewhere and cannot be forgotten through this stage, which is also how v1 behaved.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's unlocked-skill record, and the forget
 * paths additionally revalidate that player's slot bindings.</p>
 */
public final class SkillLearnStage implements CoreActionStage {

    /** Which manual-source mutation a stage instance performs. */
    public enum Operation {

        /** Unlock one skill. */
        LEARN("skill_learn", "Unlocks a skill for the target through the manual skill source."),

        /** Remove one manually unlocked skill. */
        FORGET("skill_forget", "Removes one manually unlocked skill from the target."),

        /** Remove every manually unlocked skill. */
        FORGET_ALL("skill_forget_all", "Removes all manually unlocked skills from the target.");

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
     * @param plugin owning plugin, source of the manual skill source service
     * @param operation which mutation this instance performs
     */
    public SkillLearnStage(@NotNull EmakiSkillsPlugin plugin, @NotNull Operation operation) {
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
        return operation == Operation.FORGET_ALL
                ? List.of()
                : List.of(CoreStageParameter.required("skill", CoreStageParameterType.STRING, "Skill id"));
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
        if (plugin.manualSkillSourceService() == null
                || plugin.playerSkillStateService() == null
                || plugin.playerSkillDataStore() == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        if (operation == Operation.FORGET_ALL) {
            return forgetAll(target);
        }
        String skillId = Texts.normalizeId(arguments.getString("skill"));
        if (skillId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.skills.skill_required");
        }
        return operation == Operation.LEARN ? learn(target, skillId) : forget(target, skillId);
    }

    private CoreActionOutcome learn(Player target, String skillId) {
        if (plugin.playerSkillStateService().getDefinition(skillId) == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.skills.unknown_skill", Map.of("skill", skillId));
        }
        if (!plugin.manualSkillSourceService().learn(target, skillId)) {
            // Skipped, not failed: the only reason learn refuses a known skill is that it is already
            // unlocked, so the pipeline's intent is already satisfied.
            return CoreActionOutcome.skipped("action.v2.stage.skills.already_learned");
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("skill", skillId));
    }

    private CoreActionOutcome forget(Player target, String skillId) {
        boolean forgotten = plugin.manualSkillSourceService().forget(target, skillId);
        // Revalidated even when nothing was removed, matching v1: bindings can also be stale from an
        // earlier config change, and this is the cheap point to notice.
        plugin.playerSkillStateService().validateBindings(target);
        if (!forgotten) {
            return CoreActionOutcome.skipped("action.v2.stage.skills.not_learned");
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("skill", skillId));
    }

    private CoreActionOutcome forgetAll(Player target) {
        int removed = plugin.manualSkillSourceService().forgetAll(target);
        plugin.playerSkillStateService().validateBindings(target);
        if (removed <= 0) {
            return CoreActionOutcome.skipped("action.v2.stage.skills.nothing_learned");
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("removed", removed));
    }
}
