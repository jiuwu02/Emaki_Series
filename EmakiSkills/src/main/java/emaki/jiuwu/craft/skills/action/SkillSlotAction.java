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
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;

public final class SkillSlotAction implements Action {

    public static final String EQUIP_ID = "skill_equip";
    public static final String UNEQUIP_ID = "skill_unequip";
    public static final String BIND_ID = "skill_bind";

    private final String id;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;

    SkillSlotAction(String id, PlayerSkillStateService stateService, PlayerSkillDataStore dataStore) {
        this.id = id;
        this.stateService = stateService;
        this.dataStore = dataStore;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Modify an EmakiSkills player skill slot.";
    }

    @Override
    public String category() {
        return "skills";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (EQUIP_ID.equals(id)) {
            return List.of(
                    ActionParameter.required("slot", ActionParameterType.INTEGER, "Slot index"),
                    ActionParameter.required("skill", ActionParameterType.STRING, "Skill id")
            );
        }
        if (BIND_ID.equals(id)) {
            return List.of(
                    ActionParameter.required("slot", ActionParameterType.INTEGER, "Slot index"),
                    ActionParameter.required("trigger", ActionParameterType.STRING, "Trigger id")
            );
        }
        return List.of(ActionParameter.required("slot", ActionParameterType.INTEGER, "Slot index"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id + "' requires a player context.");
        }
        int slot = ActionParsers.parseInt(arguments.get("slot"), -1);
        boolean success;
        if (EQUIP_ID.equals(id)) {
            success = stateService.equipSkill(player, slot, Texts.normalizeId(arguments.get("skill")));
        } else if (BIND_ID.equals(id)) {
            success = stateService.bindTrigger(player, slot, Texts.normalizeId(arguments.get("trigger")));
        } else {
            success = stateService.unequipSkill(player, slot);
        }
        if (!success) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Skill slot action failed: " + id);
        }
        dataStore.save(player);
        return ActionResult.ok(Map.of("slot", slot));
    }
}
