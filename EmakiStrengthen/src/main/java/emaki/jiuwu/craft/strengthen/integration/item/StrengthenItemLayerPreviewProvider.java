package emaki.jiuwu.craft.strengthen.integration.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewProvider;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewRequest;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewResult;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public final class StrengthenItemLayerPreviewProvider implements ItemLayerPreviewProvider {

    private static final String LAYER_ID = "strengthen";

    private final EmakiStrengthenPlugin plugin;

    public static AutoCloseable register(EmakiStrengthenPlugin plugin) {
        return EmakiItemApi.registerLayerPreview(plugin, new StrengthenItemLayerPreviewProvider(plugin));
    }

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
    public ItemLayerPreviewResult preview(ItemLayerPreviewRequest request) {
        ItemStack input = request == null ? null : request.currentItem();
        if (input == null || input.getType().isAir()) {
            return ItemLayerPreviewResult.unavailable(LAYER_ID, "基础物品不可用。", Map.of(), Map.of());
        }
        StrengthenState state = plugin.attemptService().readState(input);
        String requestedRecipeId = Texts.lower(option(request.options(), "recipeId"));
        String recipeId = Texts.isNotBlank(requestedRecipeId) ? requestedRecipeId : state.recipeId();
        StrengthenRecipe recipe = plugin.recipeLoader().get(recipeId);
        if (recipe == null) {
            Map<String, Object> selected = new LinkedHashMap<>();
            selected.put("recipeId", Texts.toStringSafe(recipeId));
            return new ItemLayerPreviewResult(LAYER_ID, false, "没有任何强化配方匹配当前 EmakiItem。", null, details(state, recipeId, null), options(null, state, 0, 0), selected);
        }
        int maxStar = Math.max(0, recipe.limits().maxStar());
        if (maxStar <= 0) {
            return ItemLayerPreviewResult.unavailable(LAYER_ID, "强化配方没有可预览的星级阶段。", details(state, recipeId, recipe), Map.of());
        }
        int defaultStar = Numbers.clamp(Math.max(1, state.currentStar() + 1), 1, maxStar);
        int targetStar = Numbers.clamp(Numbers.tryParseInt(request.options().get("star"), defaultStar), 1, maxStar);
        int targetTemper = Numbers.clamp(Numbers.tryParseInt(request.options().get("temper"), state.temperLevel()), 0, recipe.limits().maxTemper());
        ItemStack preview = plugin.attemptService().applyAdminState(input.clone(), targetStar, targetTemper, recipe.id());
        if (preview == null || preview.getType().isAir()) {
            return ItemLayerPreviewResult.unavailable(LAYER_ID, "强化层预览重建失败。", details(state, recipe.id(), recipe), options(recipe, state, targetStar, targetTemper));
        }
        Map<String, Object> details = details(state, recipe.id(), recipe);
        details.put("routeSummary", Map.of(
                "recipeId", recipe.id(),
                "maxStar", recipe.limits().maxStar(),
                "branching", recipe.branchTree() != null
        ));
        details.put("source", state.baseSourceSignature());
        return ItemLayerPreviewResult.available(
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
        options.put("recipeId", recipe == null ? "" : recipe.id());
        options.put("recipes", recipeOptions());
        options.put("currentStar", state == null ? 0 : state.currentStar());
        options.put("currentTemper", state == null ? 0 : state.temperLevel());
        options.put("selectedStar", selectedStar);
        options.put("selectedTemper", selectedTemper);
        if (recipe != null) {
            options.put("maxStar", recipe.limits().maxStar());
            options.put("maxTemper", recipe.limits().maxTemper());
            options.put("stars", List.copyOf(recipe.stars().keySet().stream().sorted().toList()));
        }
        return options;
    }

    private List<Map<String, String>> recipeOptions() {
        return plugin.recipeLoader().ordered().stream()
                .map(recipe -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("id", recipe.id());
                    entry.put("displayName", Texts.toStringSafe(recipe.displayName()));
                    return entry;
                })
                .toList();
    }

    private String option(Map<String, Object> options, String key) {
        if (options == null || key == null) {
            return "";
        }
        return Texts.toStringSafe(options.get(key));
    }
}
