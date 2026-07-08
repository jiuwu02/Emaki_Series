package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.service.ManualSkillSourceService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;

public final class SkillLearnAction implements Action {

    public enum Operation {
        LEARN,
        FORGET,
        FORGET_ALL
    }

    private final String id;
    private final Operation operation;
    private final ManualSkillSourceService manualSkillSourceService;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;

    SkillLearnAction(String id,
            Operation operation,
            ManualSkillSourceService manualSkillSourceService,
            PlayerSkillStateService stateService,
            PlayerSkillDataStore dataStore) {
        this.id = id;
        this.operation = operation;
        this.manualSkillSourceService = manualSkillSourceService;
        this.stateService = stateService;
        this.dataStore = dataStore;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return switch (operation) {
            case LEARN -> "Unlock an EmakiSkills skill through the manual skill source.";
            case FORGET -> "Remove one manually unlocked EmakiSkills skill.";
            case FORGET_ALL -> "Remove all manually unlocked EmakiSkills skills.";
        };
    }

    @Override
    public String category() {
        return "skills";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (operation == Operation.FORGET_ALL) {
            return List.of();
        }
        return List.of(ActionParameter.required("skill", ActionParameterType.STRING, "Skill id"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id + "' requires a player context.");
        }
        if (manualSkillSourceService == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Manual skill source service is unavailable.");
        }
        if (operation == Operation.FORGET_ALL) {
            int removed = manualSkillSourceService.forgetAll(player);
            stateService.validateBindings(player);
            if (removed > 0) {
                dataStore.save(player);
            }
            return removed <= 0 ? ActionResult.skipped("No manually learned skills were present.") : ActionResult.ok(Map.of("removed", removed));
        }
        String skillId = Texts.normalizeId(arguments.get("skill"));
        if (Texts.isBlank(skillId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action '" + id + "' requires a skill argument.");
        }
        if (operation == Operation.LEARN) {
            if (stateService.getDefinition(skillId) == null) {
                return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown skill: " + skillId);
            }
            boolean learned = manualSkillSourceService.learn(player, skillId);
            if (learned) {
                dataStore.save(player);
            }
            return learned ? ActionResult.ok(Map.of("skill", skillId)) : ActionResult.skipped("Skill is already manually learned: " + skillId);
        }
        boolean forgotten = manualSkillSourceService.forget(player, skillId);
        stateService.validateBindings(player);
        if (forgotten) {
            dataStore.save(player);
        }
        return forgotten ? ActionResult.ok(Map.of("skill", skillId)) : ActionResult.skipped("Skill is not manually learned: " + skillId);
    }
}
