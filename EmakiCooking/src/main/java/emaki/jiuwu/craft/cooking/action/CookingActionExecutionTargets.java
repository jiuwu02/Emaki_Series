package emaki.jiuwu.craft.cooking.action;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionTarget;
import emaki.jiuwu.craft.corelib.action.ActionPlanningContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

final class CookingActionExecutionTargets {

    private CookingActionExecutionTargets() {
    }

    static ActionExecutionTarget onlinePlayer(ActionPlanningContext context, String actionId) {
        String target = context == null ? "" : Texts.toStringSafe(context.arguments().get("target")).trim();
        if (Texts.isBlank(target)) {
            Player contextual = context == null || context.actionContext() == null
                    ? null
                    : context.actionContext().player();
            return contextual == null
                    ? failure(actionId, "requires an online player target")
                    : ActionExecutionTarget.entity(contextual);
        }

        UUID targetId = resolveOnlinePlayerId(target);
        Player player = targetId == null ? null : Bukkit.getPlayer(targetId);
        return player == null || !player.isOnline()
                ? failure(actionId, "could not resolve the target to an online player entity")
                : ActionExecutionTarget.entity(player);
    }

    private static UUID resolveOnlinePlayerId(String target) {
        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            Player player = Bukkit.getPlayerExact(target);
            return player == null ? null : player.getUniqueId();
        }
    }

    private static ActionExecutionTarget failure(String actionId, String reason) {
        return ActionExecutionTarget.failure(ActionResult.failure(
                ActionErrorType.INVALID_STATE,
                Texts.toStringSafe(actionId) + " " + reason + "."));
    }
}
