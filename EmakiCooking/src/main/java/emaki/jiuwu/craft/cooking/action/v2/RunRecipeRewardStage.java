package emaki.jiuwu.craft.cooking.action.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Delivers a configured recipe outcome to the target: its outputs, its actions, or both.
 *
 * <p><strong>Transitional implementation.</strong> The reward service this delegates to executes a recipe's
 * configured action lines through the v1 action executor. In v2 those lines belong in a named sequence invoked
 * with {@code run}, but named sequences are not wired up yet and the recipe files still hold v1 syntax. Rather
 * than migrate the recipe format here, this stage keeps calling the existing service; phase 6 replaces the
 * inner call with {@code run} alongside the config converter that rewrites the recipe files.</p>
 *
 * <p>Two consequences of that delegation are worth knowing. First, the reward chain is asynchronous and
 * fire-and-forget, so a successful outcome from this stage means the delivery was submitted, not that it
 * finished. Second, the nested action lines run against a context this stage does not build, so the pipeline's
 * declared-context checking does not extend into them.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: the stage itself resolves a recipe and hands one player's outputs to the
 * reward service. The nested action lines choose their own domains through the v1 executor.</p>
 */
public final class RunRecipeRewardStage implements CoreActionStage {

    private final EmakiCookingPlugin plugin;

    /**
     * Creates the stage.
     *
     * @param plugin owning plugin, source of the recipe and reward services
     */
    public RunRecipeRewardStage(@NotNull EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return "cooking_run_recipe_reward";
    }

    @Override
    public @NotNull String description() {
        return "Delivers a configured recipe outcome to the target.";
    }

    @Override
    public @NotNull String category() {
        return "cooking";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(
                CoreStageParameter.required("recipe", CoreStageParameterType.STRING, "Recipe id"),
                CoreStageParameter.optional("station", CoreStageParameterType.STRING, "",
                        "Station folder name; empty searches every station"),
                CoreStageParameter.optional("outcome", CoreStageParameterType.STRING, "success",
                        "Outcome name or path, for example success or result.perfect"),
                CoreStageParameter.optional("drop_result", CoreStageParameterType.BOOLEAN, "false",
                        "Drop outputs at the target instead of giving them"),
                CoreStageParameter.optional("include_outputs", CoreStageParameterType.BOOLEAN, "true",
                        "Deliver the configured outputs"),
                CoreStageParameter.optional("include_actions", CoreStageParameterType.BOOLEAN, "true",
                        "Execute the configured recipe actions"),
                CoreStageParameter.optional("phase", CoreStageParameterType.STRING, "",
                        "Phase name recorded for the nested actions"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (plugin.recipeService() == null || plugin.rewardService() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.cooking.reward_service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        String recipeId = Texts.trim(arguments.getString("recipe"));
        if (recipeId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.cooking.recipe_required");
        }
        String stationArgument = Texts.trim(arguments.getString("station"));
        StationType stationType = stationType(stationArgument);
        if (!stationArgument.isEmpty() && stationType == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.cooking.unknown_station", Map.of("station", stationArgument));
        }
        RecipeDocument recipe = findRecipe(recipeId, stationType);
        if (recipe == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.cooking.unknown_recipe", Map.of("recipe", recipeId));
        }
        String outcomePath = outcomePath(arguments.getString("outcome", "success"));
        Map<String, Object> outcome = plugin.recipeService().outcome(recipe, outcomePath);
        List<Map<String, Object>> outputs = arguments.getBoolean("include_outputs", true)
                ? plugin.recipeService().outputs(outcome)
                : List.of();
        List<String> actions = arguments.getBoolean("include_actions", true)
                ? plugin.recipeService().actions(outcome)
                : List.of();
        if (outputs.isEmpty() && actions.isEmpty()) {
            return CoreActionOutcome.skipped("action.v2.stage.cooking.outcome_empty");
        }
        String phase = Texts.trim(arguments.getString("phase"));
        if (phase.isEmpty()) {
            phase = "cooking.action.recipe." + recipe.id();
        }
        plugin.rewardService().deliver(recipe, target, target.getLocation(),
                arguments.getBoolean("drop_result", false), List.of(), outputs, actions, phase,
                placeholders(recipe, outcomePath));
        return CoreActionOutcome.success(Map.of(
                "recipe", recipe.id(),
                "station", recipe.stationType().folderName(),
                "outcome", outcomePath,
                "outputs", outputs.size(),
                "actions", actions.size(),
                "target", target.getUniqueId().toString()));
    }

    /**
     * Builds the placeholder set the nested actions see.
     *
     * <p>Both the short and {@code cooking_}-prefixed spellings are supplied because v1 did, and recipe files
     * in the wild use either.</p>
     */
    private Map<String, Object> placeholders(RecipeDocument recipe, String outcomePath) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("recipe_id", recipe.id());
        placeholders.put("recipe_name", recipe.displayName());
        placeholders.put("station_type", recipe.stationType().folderName());
        placeholders.put("cooking_recipe_id", recipe.id());
        placeholders.put("cooking_recipe_name", recipe.displayName());
        placeholders.put("cooking_station_type", recipe.stationType().folderName());
        placeholders.put("cooking_outcome", outcomePath);
        return Map.copyOf(placeholders);
    }

    private RecipeDocument findRecipe(String recipeId, StationType stationType) {
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

    /** Accepts either {@code success} or a full {@code result.success} path, as v1 did. */
    private String outcomePath(String value) {
        String outcome = Texts.isBlank(value) ? "success" : value.trim();
        return outcome.contains(".") ? outcome : "result." + outcome;
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
