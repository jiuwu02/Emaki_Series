package emaki.jiuwu.craft.cooking.action;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;

/** Rechecks nutrition thresholds without clearing their edge-trigger state. */
public final class NutritionThresholdRecheckAction implements Action {

    private final EmakiCookingPlugin plugin;
    private final String id;

    public NutritionThresholdRecheckAction(EmakiCookingPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Recheck EmakiCooking nutrition thresholds for an online player.";
    }

    @Override
    public String category() {
        return "emakicooking";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.optional("target", ActionParameterType.STRING, "", "Target online player name or UUID. Defaults to action context player."),
                ActionParameter.optional("silent", ActionParameterType.BOOLEAN, "false", "Whether to suppress optional output.")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (plugin.nutritionService() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCooking nutrition system is not ready.");
        }
        Player player = targetPlayer(context, arguments == null ? null : arguments.get("target"));
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, id + " requires an online player target.");
        }
        if (!plugin.nutritionService().recheckThresholds(player)) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCooking nutrition thresholds are disabled or unavailable.");
        }
        return ActionResult.ok(Map.of(
                "target", player.getUniqueId().toString(),
                "player", player.getName()
        ));
    }

    private Player targetPlayer(ActionContext context, String targetName) {
        if (Texts.isNotBlank(targetName)) {
            Player byName = Bukkit.getPlayerExact(targetName);
            if (byName != null) {
                return byName;
            }
            try {
                return Bukkit.getPlayer(UUID.fromString(targetName.trim()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return context == null ? null : context.player();
    }
}
