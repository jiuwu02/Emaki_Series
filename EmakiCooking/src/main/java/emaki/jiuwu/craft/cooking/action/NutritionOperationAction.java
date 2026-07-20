package emaki.jiuwu.craft.cooking.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionTarget;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionPlanningContext;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;








public final class NutritionOperationAction implements Action {

    private final EmakiCookingPlugin plugin;
    private final String id;
    private final NutritionOperationType operationType;

    public NutritionOperationAction(EmakiCookingPlugin plugin, String id, NutritionOperationType operationType) {
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
        return "Modify EmakiCooking player nutrition value.";
    }

    @Override
    public String category() {
        return "emakicooking";
    }

    @Override
    public ActionExecutionTarget executionTarget(ActionPlanningContext context) {
        return CookingActionExecutionTargets.onlinePlayer(context, id);
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.required("type", ActionParameterType.STRING, "Nutrition type id."),
                ActionParameter.required("amount", ActionParameterType.STRING, "Nutrition amount or expression."),
                ActionParameter.optional("target", ActionParameterType.STRING, "", "Target player name or UUID. Defaults to action context player."),
                ActionParameter.optional("silent", ActionParameterType.BOOLEAN, "false", "Whether to suppress optional output.")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (plugin.nutritionService() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCooking nutrition system is not ready.");
        }
        UUID targetId = targetId(context, arguments.get("target"));
        if (targetId == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCooking nutrition action requires a player target name or UUID.");
        }
        String type = value(arguments, "type", "");
        if (Texts.isBlank(type)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "EmakiCooking nutrition action requires a 'type'.");
        }
        double amount = parseAmount(value(arguments, "amount", "0"), context);
        NutritionOperationResult result = switch (operationType) {
            case ADD -> plugin.nutritionService().add(targetId, type, amount);
            case REMOVE -> plugin.nutritionService().remove(targetId, type, amount);
            case SET -> plugin.nutritionService().set(targetId, type, amount);
        };
        if (!result.success()) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "EmakiCooking nutrition action failed: " + result.reason());
        }
        return ActionResult.ok(Map.of(
                "type", result.typeId(),
                "old_value", result.oldValue(),
                "new_value", result.newValue()
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

    private static double parseAmount(String value, ActionContext context) {
        String expression = Texts.toStringSafe(value).trim();
        if (expression.isEmpty()) {
            return 0D;
        }
        try {
            return Double.parseDouble(expression);
        } catch (NumberFormatException ignored) {
            try {
                return ExpressionEngine.evaluate(expression, expressionVariables(context));
            } catch (RuntimeException exception) {
                return 0D;
            }
        }
    }

    private static Map<String, Object> expressionVariables(ActionContext context) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (context == null) {
            return values;
        }
        for (Map.Entry<String, String> entry : context.placeholders().entrySet()) {
            putNumber(values, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : context.attributes().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number number) {
                values.put(entry.getKey(), number.doubleValue());
            } else if (value instanceof CharSequence text) {
                putNumber(values, entry.getKey(), text.toString());
            }
        }
        return values;
    }

    private static void putNumber(Map<String, Object> values, String key, String raw) {
        try {
            values.put(Texts.lower(key), Double.parseDouble(Texts.toStringSafe(raw)));
        } catch (NumberFormatException ignored) {
        }
    }
}
