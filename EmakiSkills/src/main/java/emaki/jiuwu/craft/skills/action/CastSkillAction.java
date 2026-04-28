package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;

/**
 * CoreLib action that casts a MythicMobs skill through EmakiSkills.
 *
 * <p>Script usage: {@code castskill skill="FireballSkill"}
 */
public final class CastSkillAction implements Action {

    private final MythicSkillCastService mythicSkillCastService;

    public CastSkillAction(MythicSkillCastService mythicSkillCastService) {
        this.mythicSkillCastService = mythicSkillCastService;
    }

    @Override
    public @NotNull String id() {
        return "castskill";
    }

    @Override
    public @NotNull String description() {
        return "Cast a MythicMobs skill on the player.";
    }

    @Override
    public @NotNull String category() {
        return "skills";
    }

    @Override
    public @NotNull List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.required("skill", ActionParameterType.STRING, "MythicMobs skill ID to cast")
        );
    }

    @Override
    public @NotNull ActionResult execute(@NotNull ActionContext context, @NotNull Map<String, String> arguments) {
        Player player = context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "castskill requires a player context.");
        }

        String skillId = arguments.get("skill");
        if (Texts.isBlank(skillId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Missing required argument 'skill'.");
        }

        if (!mythicSkillCastService.isAvailable()) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "MythicMobs is not available.");
        }

        if (!mythicSkillCastService.skillExists(skillId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown MythicMobs skill: " + skillId);
        }

        boolean success = mythicSkillCastService.cast(player, skillId);
        return success
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Skill cast failed: " + skillId);
    }
}
