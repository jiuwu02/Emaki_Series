package emaki.jiuwu.craft.cooking.apiimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingOperations;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingRewardService;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;

public final class DefaultCookingOperations implements CookingOperations {

    private final EmakiCookingPlugin plugin;

    public DefaultCookingOperations(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public EmakiResult<List<ItemStack>> createOutputs(CookingStationType stationType, String recipeId) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (stationType == null) {
            return EmakiResult.invalidInput("cooking.input.station_type_missing");
        }
        if (Texts.isBlank(recipeId)) {
            return EmakiResult.invalidInput("cooking.input.recipe_id_missing");
        }
        RecipeDocument recipe = document(stationType, Texts.lower(recipeId));
        if (recipe == null) {
            return EmakiResult.notFound("cooking.recipe_not_found");
        }
        CookingRecipeService recipeService = plugin.recipeService();
        CookingRewardService rewardService = plugin.rewardService();
        List<Map<String, Object>> configuredOutputs = recipeService.outputs(recipe);
        if (configuredOutputs.isEmpty()) {
            return EmakiResult.success(List.of());
        }
        List<ItemStack> created = new ArrayList<>();
        int failed = 0;
        for (Map<String, Object> output : configuredOutputs) {
            ItemStack item = rewardService.createOutputItem(
                    recipe,
                    output,
                    null,
                    null,
                    "cooking.api.create_outputs",
                    Map.of("recipe_id", recipe.id(), "station_type", stationType.configKey()));
            if (item == null || item.getType().isAir()) {
                failed++;
            } else {
                created.add(item);
            }
        }
        List<ItemStack> immutable = List.copyOf(created);
        if (failed == 0) {
            return EmakiResult.success(immutable);
        }
        if (!immutable.isEmpty()) {
            return EmakiResult.partial(immutable, "cooking.output.partial_creation");
        }
        return EmakiResult.internalError("cooking.output.creation_failed");
    }

    @Override
    public EmakiResult<Boolean> completionConditionPasses(String recipeId, Player player) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (Texts.isBlank(recipeId)) {
            return EmakiResult.invalidInput("cooking.input.recipe_id_missing");
        }
        if (player == null) {
            return EmakiResult.invalidInput("cooking.input.player_missing");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        List<RecipeDocument> matches = documentsById(Texts.lower(recipeId));
        if (matches.isEmpty()) {
            return EmakiResult.notFound("cooking.recipe_not_found");
        }
        if (matches.size() > 1) {
            return EmakiResult.failure(
                    FailureKind.REJECTED,
                    "cooking.recipe_id_ambiguous",
                    Map.of("recipe_id", Texts.lower(recipeId), "matches", matches.size()));
        }
        return EmakiResult.success(plugin.rewardService().completionConditionPasses(matches.getFirst(), player));
    }

    private RecipeDocument document(CookingStationType type, String recipeId) {
        return switch (type) {
            case CHOPPING_BOARD -> plugin.choppingBoardRecipeLoader().get(recipeId);
            case WOK -> plugin.wokRecipeLoader().get(recipeId);
            case GRINDER -> plugin.grinderRecipeLoader().get(recipeId);
            case STEAMER -> plugin.steamerRecipeLoader().get(recipeId);
            case OVEN -> plugin.ovenRecipeLoader().get(recipeId);
            case JUICER -> plugin.juicerRecipeLoader().get(recipeId);
            case FERMENTATION_BARREL -> plugin.fermentationBarrelRecipeLoader().get(recipeId);
        };
    }

    private List<RecipeDocument> documentsById(String recipeId) {
        List<RecipeDocument> matches = new ArrayList<>();
        for (CookingStationType type : CookingStationType.values()) {
            RecipeDocument document = document(type, recipeId);
            if (document != null) {
                matches.add(document);
            }
        }
        return List.copyOf(matches);
    }

    private boolean ready() {
        return plugin != null
                && plugin.isEnabled()
                && plugin.publicApiReady()
                && plugin.threadOwnership() != null
                && plugin.recipeService() != null
                && plugin.rewardService() != null
                && plugin.choppingBoardRecipeLoader() != null
                && plugin.wokRecipeLoader() != null
                && plugin.grinderRecipeLoader() != null
                && plugin.steamerRecipeLoader() != null
                && plugin.ovenRecipeLoader() != null
                && plugin.juicerRecipeLoader() != null
                && plugin.fermentationBarrelRecipeLoader() != null;
    }
}
