package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewProvider;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewRequest;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewResult;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public final class StrengthenItemLayerPreviewProvider implements WebItemLayerPreviewProvider {

    private static final String LAYER_ID = "strengthen";

    private final EmakiStrengthenPlugin plugin;

    public StrengthenItemLayerPreviewProvider(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return LAYER_ID;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public WebItemLayerPreviewResult preview(WebItemLayerPreviewRequest request) {
        ItemStack input = request == null ? null : request.currentItem();
        if (input == null || input.getType().isAir()) {
            return WebItemLayerPreviewResult.unavailable(LAYER_ID, "基础物品不可用。", Map.of(), Map.of());
        }
        StrengthenState state = plugin.attemptService().readState(input);
        String requestedRecipeId = Texts.lower(option(request.options(), "recipeId"));
        String recipeId = Texts.isNotBlank(requestedRecipeId) ? requestedRecipeId : state.recipeId();
        StrengthenRecipe recipe = plugin.recipeLoader().get(recipeId);
        if (recipe == null) {
            return WebItemLayerPreviewResult.unavailable(LAYER_ID, "没有任何强化配方匹配当前 EmakiItem。", details(state, recipeId, null), Map.of());
        }
        int maxStar = Math.max(0, recipe.limits().maxStar());
        if (maxStar <= 0) {
            return WebItemLayerPreviewResult.unavailable(LAYER_ID, "强化配方没有可预览的星级阶段。", details(state, recipeId, recipe), Map.of());
        }
        int defaultStar = Numbers.clamp(Math.max(1, state.currentStar() + 1), 1, maxStar);
        int targetStar = Numbers.clamp(Numbers.tryParseInt(request.options().get("star"), defaultStar), 1, maxStar);
        int targetTemper = Numbers.clamp(Numbers.tryParseInt(request.options().get("temper"), state.temperLevel()), 0, recipe.limits().maxTemper());
        ItemStack preview = plugin.attemptService().applyAdminState(input.clone(), targetStar, targetTemper, recipe.id());
        if (preview == null || preview.getType().isAir()) {
            return WebItemLayerPreviewResult.unavailable(LAYER_ID, "强化层预览重建失败。", details(state, recipe.id(), recipe), options(recipe, state, targetStar, targetTemper));
        }
        Map<String, Object> details = details(state, recipe.id(), recipe);
        details.put("route", plugin.routePreviewService().preview(recipe.id()));
        details.put("source", state.baseSourceSignature());
        return WebItemLayerPreviewResult.available(
                LAYER_ID,
                "已按真实强化层重建预览。",
                preview,
                details,
                options(recipe, state, targetStar, targetTemper),
                Map.of("recipeId", recipe.id(), "star", targetStar, "temper", targetTemper)
        );
    }

    private Map<String, Object> details(StrengthenState state, String recipeId, StrengthenRecipe recipe) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("recipeId", Texts.toStringSafe(recipeId));
        details.put("currentStar", state == null ? 0 : state.currentStar());
        details.put("currentTemper", state == null ? 0 : state.temperLevel());
        details.put("hasLayer", state != null && state.hasLayer());
        details.put("eligible", state != null && state.eligible());
        details.put("eligibleReason", state == null ? "" : state.eligibleReason());
        if (recipe != null) {
            details.put("displayName", recipe.displayName());
            details.put("maxStar", recipe.limits().maxStar());
            details.put("maxTemper", recipe.limits().maxTemper());
            details.put("branching", recipe.branchTree() != null);
        }
        return details;
    }

    private Map<String, Object> options(StrengthenRecipe recipe, StrengthenState state, int selectedStar, int selectedTemper) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("recipeId", recipe.id());
        options.put("currentStar", state == null ? 0 : state.currentStar());
        options.put("currentTemper", state == null ? 0 : state.temperLevel());
        options.put("selectedStar", selectedStar);
        options.put("selectedTemper", selectedTemper);
        options.put("maxStar", recipe.limits().maxStar());
        options.put("maxTemper", recipe.limits().maxTemper());
        options.put("stars", List.copyOf(recipe.stars().keySet().stream().sorted().toList()));
        return options;
    }

    private String option(Map<String, Object> options, String key) {
        if (options == null || key == null) {
            return "";
        }
        return Texts.toStringSafe(options.get(key));
    }
}
