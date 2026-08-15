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
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

public final class SkillLevelStage implements CoreActionStage {

    public enum Operation {

        SET_LEVEL("skill_set_level", "Sets one of the target's skill levels directly."),

        UPGRADE("skill_upgrade", "Upgrades one of the target's skills through the upgrade service.");

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

    public SkillLevelStage(@NotNull EmakiSkillsPlugin plugin, @NotNull Operation operation) {
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
            case SET_LEVEL -> List.of(
                    CoreStageParameter.required("skill", CoreStageParameterType.STRING, "Skill id"),
                    CoreStageParameter.required("level", CoreStageParameterType.INTEGER, "Target level"));
            case UPGRADE -> List.of(
                    CoreStageParameter.required("skill", CoreStageParameterType.STRING, "Skill id"));
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
        String skillId = Texts.normalizeId(arguments.getString("skill"));
        SkillDefinition definition = plugin.playerSkillStateService().getDefinition(skillId);
        if (definition == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.skills.unknown_skill", Map.of("skill", skillId));
        }
        return operation == Operation.SET_LEVEL
                ? setLevel(target, definition, skillId, arguments)
                : upgrade(target, skillId);
    }

    private CoreActionOutcome setLevel(Player target,
            SkillDefinition definition,
            String skillId,
            CoreResolvedArguments arguments) {
        if (plugin.skillLevelService() == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        int oldLevel = plugin.skillLevelService().currentLevel(target, definition);

        int newLevel = plugin.skillLevelService().setLevel(target, definition,
                arguments.getInt("level", oldLevel));
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of(
                "skill", skillId,
                "old_level", oldLevel,
                "new_level", newLevel));
    }

    private CoreActionOutcome upgrade(Player target, String skillId) {
        if (plugin.skillUpgradeService() == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        SkillUpgradeService.UpgradeResult result = plugin.skillUpgradeService().upgrade(target, skillId);
        if (!result.success()) {

            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.skills.upgrade_failed",
                    Map.of("reason", Texts.toStringSafe(result.messageKey())));
        }
        SkillUpgradeService.UpgradePreview preview = result.preview();
        return CoreActionOutcome.success(Map.of(
                "skill", skillId,
                "level_changed", result.levelChanged(),
                "message", Texts.toStringSafe(result.messageKey()),
                "current_level", preview == null ? 0 : preview.currentLevel(),
                "target_level", preview == null ? 0 : preview.targetLevel()));
    }
}
