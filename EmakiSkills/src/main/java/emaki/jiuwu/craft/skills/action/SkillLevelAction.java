package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

public final class SkillLevelAction implements Action {

    public static final String SET_LEVEL_ID = "skill_setlevel";
    public static final String UPGRADE_ID = "skill_upgrade";

    private final String id;
    private final PlayerSkillStateService stateService;
    private final SkillLevelService levelService;
    private final SkillUpgradeService upgradeService;
    private final PlayerSkillDataStore dataStore;

    SkillLevelAction(String id,
            PlayerSkillStateService stateService,
            SkillLevelService levelService,
            SkillUpgradeService upgradeService,
            PlayerSkillDataStore dataStore) {
        this.id = id;
        this.stateService = stateService;
        this.levelService = levelService;
        this.upgradeService = upgradeService;
        this.dataStore = dataStore;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return SET_LEVEL_ID.equals(id) ? "Set an EmakiSkills skill level." : "Upgrade an EmakiSkills skill through the upgrade service.";
    }

    @Override
    public String category() {
        return "skills";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (SET_LEVEL_ID.equals(id)) {
            return List.of(
                    ActionParameter.required("skill", ActionParameterType.STRING, "Skill id"),
                    ActionParameter.required("level", ActionParameterType.INTEGER, "Target level")
            );
        }
        return List.of(ActionParameter.required("skill", ActionParameterType.STRING, "Skill id"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id + "' requires a player context.");
        }
        String skillId = Texts.normalizeId(arguments.get("skill"));
        SkillDefinition definition = stateService == null ? null : stateService.getDefinition(skillId);
        if (definition == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown skill: " + skillId);
        }
        if (SET_LEVEL_ID.equals(id)) {
            int oldLevel = levelService.currentLevel(player, definition);
            int newLevel = levelService.setLevel(player, definition, ActionParsers.parseInt(arguments.get("level"), oldLevel));
            dataStore.save(player);
            return ActionResult.ok(Map.of("skill", skillId, "old_level", oldLevel, "new_level", newLevel));
        }
        SkillUpgradeService.UpgradeResult result = upgradeService.upgrade(player, skillId);
        if (!result.success()) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Skill upgrade failed: " + result.messageKey());
        }
        SkillUpgradeService.UpgradePreview preview = result.preview();
        return ActionResult.ok(Map.of(
                "skill", skillId,
                "level_changed", result.levelChanged(),
                "message", result.messageKey(),
                "current_level", preview == null ? 0 : preview.currentLevel(),
                "target_level", preview == null ? 0 : preview.targetLevel()
        ));
    }
}
