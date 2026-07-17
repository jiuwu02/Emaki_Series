package emaki.jiuwu.craft.codex.action;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionTarget;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionPlanningContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

/** Re-sends registered EmakiCodex advancement packets to one online player. */
public final class ResyncAdvancementAction implements Action {

    private final EmakiCodexPlugin plugin;
    private final String id;

    public ResyncAdvancementAction(EmakiCodexPlugin plugin) {
        this(plugin, "codex-resync-advancement");
    }

    public ResyncAdvancementAction(EmakiCodexPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = Texts.normalizeId(id);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String category() {
        return "codex";
    }

    @Override
    public String description() {
        return "Resync EmakiCodex advancements for the player.";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(ActionParameter.optional("target", ActionParameterType.STRING, "", "Target online player name or UUID. Defaults to action context player."));
    }

    @Override
    public ActionExecutionTarget executionTarget(ActionPlanningContext context) {
        Player target = targetPlayer(
                context == null ? null : context.actionContext(),
                context == null ? null : context.arguments().get("target")
        );
        return target == null
                ? ActionExecutionTarget.failure(ActionResult.failure(
                        ActionErrorType.INVALID_STATE, id + " requires an online player target."))
                : ActionExecutionTarget.entity(target);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (plugin.advancementPacketGateway() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCodex advancement packet gateway is not ready.");
        }
        Player player = targetPlayer(context, arguments == null ? null : arguments.get("target"));
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, id + " requires an online player target.");
        }
        if (!plugin.advancementPacketGateway().canResync()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "PacketEvents is not available; EmakiCodex advancement resync cannot run.");
        }
        if (!plugin.advancementPacketGateway().resync(player)) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "EmakiCodex advancement resync failed for " + player.getName() + ".");
        }
        return ActionResult.ok(Map.of(
                "target", player.getUniqueId().toString(),
                "player", player.getName()
        ));
    }

    private Player targetPlayer(ActionContext context, String targetName) {
        if (Texts.isNotBlank(targetName)) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                return target;
            }
            try {
                return Bukkit.getPlayer(java.util.UUID.fromString(targetName.trim()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return context == null ? null : context.player();
    }
}
