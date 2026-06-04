package emaki.jiuwu.craft.level.action;

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
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelOperationType;

final class LevelOperationAction implements Action {

    private final EmakiLevelPlugin plugin;
    private final String id;
    private final LevelOperationType operationType;

    LevelOperationAction(EmakiLevelPlugin plugin, String id, LevelOperationType operationType) {
        this.plugin = plugin;
        this.id = id;
        this.operationType = operationType;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Modify EmakiLevel player data.";
    }

    @Override
    public String category() {
        return "emakilevel";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.required("type", ActionParameterType.STRING, "Level type id."),
                ActionParameter.required("amount", ActionParameterType.DOUBLE, "Experience or level amount."),
                ActionParameter.optional("target", ActionParameterType.STRING, "", "Target player name. Defaults to action context player."),
                ActionParameter.optional("reason", ActionParameterType.STRING, "action", "Operation reason."),
                ActionParameter.optional("auto_upgrade", ActionParameterType.BOOLEAN, "true", "Whether addexp should auto-upgrade."),
                ActionParameter.optional("silent", ActionParameterType.BOOLEAN, "false", "Whether to suppress optional output.")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        UUID targetId = targetId(context, arguments.get("target"));
        if (targetId == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiLevel action requires a player target name or UUID.");
        }
        String type = value(arguments, "type", plugin.appConfig().primaryType());
        double amount = parseDouble(value(arguments, "amount", "0"));
        String reason = value(arguments, "reason", "action");
        boolean autoUpgrade = Boolean.parseBoolean(value(arguments, "auto_upgrade", "true"));
        boolean silent = Boolean.parseBoolean(value(arguments, "silent", "false"));
        LevelOperationResult result = switch (operationType) {
            case ADD_EXP -> plugin.levelService().addExp(targetId, type, amount, reason, autoUpgrade, silent);
            case SET_EXP -> plugin.levelService().setExp(targetId, type, amount, reason);
            case REMOVE_EXP -> plugin.levelService().removeExp(targetId, type, amount, reason);
            case ADD_LEVEL -> plugin.levelService().addLevel(targetId, type, (int) Math.round(amount), reason);
            case SET_LEVEL -> plugin.levelService().setLevel(targetId, type, (int) Math.round(amount), reason);
            case REMOVE_LEVEL -> plugin.levelService().removeLevel(targetId, type, (int) Math.round(amount), reason);
            default -> LevelOperationResult.failure("unsupported_operation", operationType, type);
        };
        if (!result.success()) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "EmakiLevel action failed: " + result.reason());
        }
        return ActionResult.ok(Map.of(
                "type", result.typeId(),
                "old_level", result.oldLevel(),
                "new_level", result.newLevel(),
                "old_exp", result.oldExp(),
                "new_exp", result.newExp(),
                "amount", result.amount()
        ));
    }

    private UUID targetId(ActionContext context, String targetName) {
        if (Texts.isNotBlank(targetName)) {
            Player player = Bukkit.getPlayerExact(targetName);
            if (player != null) {
                return player.getUniqueId();
            }
            try {
                return UUID.fromString(targetName.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Player contextPlayer = context == null ? null : context.player();
        return contextPlayer == null ? null : contextPlayer.getUniqueId();
    }

    private static String value(Map<String, String> arguments, String key, String fallback) {
        String value = arguments == null ? null : arguments.get(key);
        return Texts.isBlank(value) ? fallback : value;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0D;
        }
    }
}
