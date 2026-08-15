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

public final class SkillLearnStage implements CoreActionStage {

    public enum Operation {

        LEARN("skill_learn", "Unlocks a skill for the target through the manual skill source."),

        FORGET("skill_forget", "Removes one manually unlocked skill from the target."),

        FORGET_ALL("skill_forget_all", "Removes all manually unlocked skills from the target.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final EmakiSkillsPlugin plugin;
    private final Operation operation;

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
            return CoreActionOutcome.skipped("action.stage.common.not_player");
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
                    "action.stage.skills.skill_required");
        }
        return operation == Operation.LEARN ? learn(target, skillId) : forget(target, skillId);
    }

    private CoreActionOutcome learn(Player target, String skillId) {
        if (plugin.playerSkillStateService().getDefinition(skillId) == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.skills.unknown_skill", Map.of("skill", skillId));
        }
        if (!plugin.manualSkillSourceService().learn(target, skillId)) {

            return CoreActionOutcome.skipped("action.stage.skills.already_learned");
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("skill", skillId));
    }

    private CoreActionOutcome forget(Player target, String skillId) {
        boolean forgotten = plugin.manualSkillSourceService().forget(target, skillId);

        plugin.playerSkillStateService().validateBindings(target);
        if (!forgotten) {
            return CoreActionOutcome.skipped("action.stage.skills.not_learned");
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("skill", skillId));
    }

    private CoreActionOutcome forgetAll(Player target) {
        int removed = plugin.manualSkillSourceService().forgetAll(target);
        plugin.playerSkillStateService().validateBindings(target);
        if (removed <= 0) {
            return CoreActionOutcome.skipped("action.stage.skills.nothing_learned");
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("removed", removed));
    }
}
