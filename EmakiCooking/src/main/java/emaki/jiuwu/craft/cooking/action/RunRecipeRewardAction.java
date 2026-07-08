package emaki.jiuwu.craft.cooking.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationType;

/** Runs configured recipe reward outputs/actions through CookingRewardService's unified outlet. */
public final class RunRecipeRewardAction implements Action {

    private final EmakiCookingPlugin plugin;
    private final String id;

    public RunRecipeRewardAction(EmakiCookingPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Deliver a configured EmakiCooking recipe outcome through CookingRewardService.";
    }

    @Override
    public String category() {
        return "emakicooking";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.required("recipe", ActionParameterType.STRING, "Recipe id."),
                ActionParameter.optional("station", ActionParameterType.STRING, "", "Station folder name. Empty searches all station loaders."),
                ActionParameter.optional("outcome", ActionParameterType.STRING, "success", "Outcome name or path, e.g. success, perfect, result.success."),
                ActionParameter.optional("target", ActionParameterType.STRING, "", "Target online player name or UUID. Defaults to action context player."),
                ActionParameter.optional("drop_result", ActionParameterType.BOOLEAN, "false", "Whether outputs should drop at the player location instead of going to inventory."),
                ActionParameter.optional("include_outputs", ActionParameterType.BOOLEAN, "true", "Whether to deliver configured outputs."),
                ActionParameter.optional("include_actions", ActionParameterType.BOOLEAN, "true", "Whether to execute configured recipe actions."),
                ActionParameter.optional("phase", ActionParameterType.STRING, "", "Action phase override."),
                ActionParameter.optional("silent", ActionParameterType.BOOLEAN, "false", "Whether to suppress optional output.")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (plugin.recipeService() == null || plugin.rewardService() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCooking recipe reward services are not ready.");
        }
        Player player = targetPlayer(context, value(arguments, "target", ""));
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, id + " requires an online player target.");
        }
        String recipeId = value(arguments, "recipe", "");
        if (Texts.isBlank(recipeId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, id + " requires a 'recipe' argument.");
        }
        String stationArgument = value(arguments, "station", "");
        StationType stationType = stationType(stationArgument);
        if (Texts.isNotBlank(stationArgument) && stationType == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown EmakiCooking station type: " + stationArgument);
        }
        RecipeDocument recipe = findRecipe(recipeId, stationType);
        if (recipe == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "EmakiCooking recipe is not loaded: " + recipeId);
        }

        String outcomePath = outcomePath(value(arguments, "outcome", "success"));
        Map<String, Object> outcome = plugin.recipeService().outcome(recipe, outcomePath);
        boolean includeOutputs = parseBoolean(value(arguments, "include_outputs", "true"), true);
        boolean includeActions = parseBoolean(value(arguments, "include_actions", "true"), true);
        List<Map<String, Object>> outputs = includeOutputs ? plugin.recipeService().outputs(outcome) : List.of();
        List<String> actions = includeActions ? plugin.recipeService().actions(outcome) : List.of();
        if (outputs.isEmpty() && actions.isEmpty()) {
            return ActionResult.skipped("Selected EmakiCooking recipe outcome has no enabled outputs or actions.");
        }

        String phase = value(arguments, "phase", "");
        if (Texts.isBlank(phase)) {
            phase = "cooking.action.recipe." + recipe.id();
        }
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("recipe_id", recipe.id());
        placeholders.put("recipe_name", recipe.displayName());
        placeholders.put("station_type", recipe.stationType().folderName());
        placeholders.put("cooking_recipe_id", recipe.id());
        placeholders.put("cooking_recipe_name", recipe.displayName());
        placeholders.put("cooking_station_type", recipe.stationType().folderName());
        placeholders.put("cooking_outcome", outcomePath);

        plugin.rewardService().deliver(
                recipe,
                player,
                location(context, player),
                parseBoolean(value(arguments, "drop_result", "false"), false),
                List.of(),
                outputs,
                actions,
                phase,
                placeholders
        );
        return ActionResult.ok(Map.of(
                "recipe", recipe.id(),
                "station", recipe.stationType().folderName(),
                "outcome", outcomePath,
                "outputs", outputs.size(),
                "actions", actions.size(),
                "target", player.getUniqueId().toString()
        ));
    }

    private RecipeDocument findRecipe(String recipeId, StationType stationType) {
        if (Texts.isBlank(recipeId)) {
            return null;
        }
        if (stationType != null) {
            return recipeFromStation(recipeId, stationType);
        }
        for (StationType candidate : StationType.values()) {
            RecipeDocument recipe = recipeFromStation(recipeId, candidate);
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    private RecipeDocument recipeFromStation(String recipeId, StationType stationType) {
        return switch (stationType) {
            case CHOPPING_BOARD -> plugin.choppingBoardRecipeLoader().get(recipeId);
            case WOK -> plugin.wokRecipeLoader().get(recipeId);
            case GRINDER -> plugin.grinderRecipeLoader().get(recipeId);
            case STEAMER -> plugin.steamerRecipeLoader().get(recipeId);
            case OVEN -> plugin.ovenRecipeLoader().get(recipeId);
            case JUICER -> plugin.juicerRecipeLoader().get(recipeId);
            case FERMENTATION_BARREL -> plugin.fermentationBarrelRecipeLoader().get(recipeId);
        };
    }

    private StationType stationType(String value) {
        if (Texts.isBlank(value)) {
            return null;
        }
        String normalized = Texts.normalizeId(value);
        for (StationType stationType : StationType.values()) {
            if (stationType.folderName().equalsIgnoreCase(normalized)
                    || stationType.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return stationType;
            }
        }
        return null;
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

    private Location location(ActionContext context, Player player) {
        if (context != null && context.attributes().get("location") instanceof Location location) {
            return location;
        }
        return player.getLocation();
    }

    private String outcomePath(String value) {
        String outcome = Texts.isBlank(value) ? "success" : value.trim();
        return outcome.contains(".") ? outcome : "result." + outcome;
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (Texts.isBlank(value)) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static String value(Map<String, String> arguments, String key, String fallback) {
        String value = arguments == null ? null : arguments.get(key);
        return Texts.isBlank(value) ? fallback : value;
    }
}
