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
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;

/** Clears nutrition to type minimums or resets it to configured defaults for one target. */
public final class NutritionResetAction implements Action {

    public enum Mode {
        CLEAR,
        RESET
    }

    private final EmakiCookingPlugin plugin;
    private final String id;
    private final Mode mode;

    public NutritionResetAction(EmakiCookingPlugin plugin, String id, Mode mode) {
        this.plugin = plugin;
        this.id = id;
        this.mode = mode;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return switch (mode) {
            case CLEAR -> "Clear EmakiCooking nutrition to configured minimum values.";
            case RESET -> "Reset EmakiCooking nutrition to configured default values.";
        };
    }

    @Override
    public String category() {
        return "emakicooking";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.optional("type", ActionParameterType.STRING, "", "Nutrition type id. Empty resets all registered types."),
                ActionParameter.optional("target", ActionParameterType.STRING, "", "Target player name or UUID. Defaults to action context player."),
                ActionParameter.optional("silent", ActionParameterType.BOOLEAN, "false", "Whether to suppress optional output.")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (plugin.nutritionService() == null || plugin.nutritionTypeRegistry() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCooking nutrition system is not ready.");
        }
        UUID targetId = targetId(context, value(arguments, "target", ""));
        if (targetId == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, id + " requires a player target name or UUID.");
        }

        String requestedType = value(arguments, "type", "");
        List<NutritionTypeConfig> types;
        if (Texts.isBlank(requestedType)) {
            types = List.copyOf(plugin.nutritionTypeRegistry().all());
        } else {
            NutritionTypeConfig type = plugin.nutritionTypeRegistry().type(requestedType).orElse(null);
            if (type == null) {
                return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown EmakiCooking nutrition type: " + requestedType);
            }
            types = List.of(type);
        }
        if (types.isEmpty()) {
            return ActionResult.skipped("No EmakiCooking nutrition types are registered.");
        }

        int changed = 0;
        Map<String, Object> values = new LinkedHashMap<>();
        for (NutritionTypeConfig type : types) {
            double targetValue = mode == Mode.CLEAR ? type.min() : type.defaultValue();
            NutritionOperationResult result = plugin.nutritionService().set(targetId, type.id(), targetValue);
            if (!result.success()) {
                return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                        id + " failed for nutrition type '" + type.id() + "': " + result.reason());
            }
            if (Double.compare(result.oldValue(), result.newValue()) != 0) {
                changed++;
            }
            values.put(type.id(), result.newValue());
        }

        return ActionResult.ok(Map.of(
                "target", targetId.toString(),
                "operation", mode.name().toLowerCase(java.util.Locale.ROOT),
                "types", types.size(),
                "changed", changed,
                "values", values
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
}
