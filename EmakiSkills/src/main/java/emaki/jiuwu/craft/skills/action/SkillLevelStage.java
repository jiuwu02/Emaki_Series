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
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

/**
 * Sets or upgrades one of the target's skill levels.
 *
 * <p>Replaces the legacy {@code SkillLevelAction}, which registered {@code skill_setlevel} and
 * {@code skill_upgrade}. Both ids gain the {@code skill_} prefix plus underscore separators to match the
 * other modules' stage naming, so {@code skill_setlevel} becomes {@code skill_set_level}.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's skill record, and upgrading additionally
 * charges that player's currencies and inventory materials.</p>
 */
public final class SkillLevelStage implements CoreActionStage {

    /** Which level mutation a stage instance performs. */
    public enum Operation {

        /** Write the level directly, ignoring upgrade costs and success rolls. */
        SET_LEVEL("skill_set_level", "Sets one of the target's skill levels directly."),

        /** Run the upgrade service, which charges costs and rolls for success. */
        UPGRADE("skill_upgrade", "Upgrades one of the target's skills through the upgrade service.");

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
     * @param plugin owning plugin, source of the skill services
     * @param operation which mutation this instance performs
     */
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
        // v1 fell back to the current level when `level` could not be parsed, which makes an unreadable
        // value a no-op instead of a reset to zero.
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
            // REJECTED rather than INTERNAL_ERROR: the upgrade service declined by its own rules, such as
            // an insufficient balance or an already maxed skill. Its message key is passed through so the
            // existing player-facing text still applies.
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
