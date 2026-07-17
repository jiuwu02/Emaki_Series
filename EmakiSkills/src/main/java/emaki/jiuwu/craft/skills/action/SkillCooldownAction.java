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
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;

public final class SkillCooldownAction implements Action {

    public static final String CLEAR_ID = "skill_clearcooldown";
    public static final String SET_ID = "skill_setcooldown";

    private final String id;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;

    SkillCooldownAction(String id, PlayerSkillStateService stateService, PlayerSkillDataStore dataStore) {
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
        return SET_ID.equals(id) ? "Set an EmakiSkills skill cooldown." : "Clear EmakiSkills cooldowns.";
    }

    @Override
    public String category() {
        return "skills";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (SET_ID.equals(id)) {
            return List.of(
                    ActionParameter.required("skill", ActionParameterType.STRING, "Skill id"),
                    ActionParameter.required("duration_ticks", ActionParameterType.TIME, "Cooldown duration in ticks")
            );
        }
        return List.of(ActionParameter.optional("skill", ActionParameterType.STRING, "", "Skill id; empty clears all cooldowns"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id + "' requires a player context.");
        }
        PlayerSkillProfile profile = stateService.getProfile(player);
        if (profile == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Skill profile is unavailable.");
        }
        String skillId = Texts.normalizeId(arguments.get("skill"));
        if (SET_ID.equals(id)) {
            if (stateService.getDefinition(skillId) == null) {
                return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown skill: " + skillId);
            }
            long ticks = ActionParsers.parseTicks(arguments.get("duration_ticks"));
            dataStore.mutate(player, current -> {
                if (ticks <= 0L) {
                    current.timingState().skillCooldownUntilBySkillId().remove(skillId);
                } else {
                    current.timingState().skillCooldownUntilBySkillId().put(
                            skillId, System.currentTimeMillis() + ticks * 50L);
                }
                current.markDirty();
            });
            dataStore.save(player);
            return ActionResult.ok(Map.of("skill", skillId, "duration_ticks", ticks));
        }
        if (Texts.isBlank(skillId)) {
            dataStore.mutate(player, current -> {
                current.timingState().clearAll();
                current.markDirty();
            });
            dataStore.save(player);
            return ActionResult.ok(Map.of("all", true));
        }
        dataStore.mutate(player, current -> {
            current.timingState().skillCooldownUntilBySkillId().remove(skillId);
            current.markDirty();
        });
        dataStore.save(player);
        return ActionResult.ok(Map.of("all", false, "skill", skillId));
    }
}
