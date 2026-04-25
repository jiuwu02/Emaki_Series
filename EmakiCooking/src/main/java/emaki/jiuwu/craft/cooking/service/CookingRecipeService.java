package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import org.bukkit.entity.Player;

public final class CookingRecipeService {

    private final EmakiCookingPlugin plugin;
    private final CookingSettingsService settingsService;
    private final Map<RecipeDocument, ItemSource> parsedSourceCache = new ConcurrentHashMap<>();

    public CookingRecipeService(EmakiCookingPlugin plugin, CookingSettingsService settingsService) {
        this.plugin = plugin;
        this.settingsService = settingsService;
    }

    public RecipeDocument findChoppingBoardRecipe(String inputSource, Player player) {
        return findByInput(plugin.choppingBoardRecipeLoader().all().values(), inputSource, player);
    }

    public RecipeDocument findGrinderRecipe(String inputSource, Player player) {
        return findByInput(plugin.grinderRecipeLoader().all().values(), inputSource, player);
    }

    public RecipeDocument grinderRecipeById(String recipeId) {
        return Texts.isBlank(recipeId) ? null : plugin.grinderRecipeLoader().get(recipeId);
    }

    public Collection<RecipeDocument> wokRecipes() {
        Collection<RecipeDocument> recipes = plugin.wokRecipeLoader().all().values();
        return recipes == null || recipes.isEmpty() ? List.of() : List.copyOf(recipes);
    }

    public int choppingCutsRequired(RecipeDocument recipe) {
        return recipe == null ? 0 : recipe.configuration().getInt("cuts_required", 0);
    }

    public int choppingToolDamage(RecipeDocument recipe) {
        return recipe == null ? 1 : Math.max(1, recipe.configuration().getInt("tool_damage", 1));
    }

    public Integer choppingDamageChance(RecipeDocument recipe) {
        if (recipe == null) {
            return settingsService.choppingCutDamageEnabled() ? settingsService.choppingCutDamageChance() : null;
        }
        if (recipe.configuration().contains("damage_override.chance")) {
            return recipe.configuration().getInt("damage_override.chance", 0);
        }
        return settingsService.choppingCutDamageEnabled() ? settingsService.choppingCutDamageChance() : null;
    }

    public Integer choppingDamageValue(RecipeDocument recipe) {
        if (recipe == null) {
            return settingsService.choppingCutDamageEnabled() ? settingsService.choppingCutDamageValue() : null;
        }
        if (recipe.configuration().contains("damage_override.value")) {
            return recipe.configuration().getInt("damage_override.value", 0);
        }
        return settingsService.choppingCutDamageEnabled() ? settingsService.choppingCutDamageValue() : null;
    }

    public List<Map<String, Object>> outputs(RecipeDocument recipe) {
        return recipe == null ? List.of() : mapList(recipe.configuration().getMapList("result.outputs"));
    }

    public List<String> actions(RecipeDocument recipe) {
        return recipe == null ? List.of() : recipe.configuration().getStringList("result.actions");
    }

    public int grinderTimeSeconds(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("grind_time_seconds", 0));
    }

    public RecipeDocument findSteamerRecipe(String inputSource, Player player) {
        return findByInput(plugin.steamerRecipeLoader().all().values(), inputSource, player);
    }

    public int steamerRequiredSteam(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("required_steam", 0));
    }

    public List<Map<String, Object>> wokIngredients(RecipeDocument recipe) {
        return recipe == null ? List.of() : mapList(recipe.configuration().getMapList("ingredients"));
    }

    public int wokHeatLevel(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("heat_level", 0));
    }

    public int wokFaultTolerance(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("fault_tolerance", 0));
    }

    public int wokStirTotalMin(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("stir_total.min", 0));
    }

    public int wokStirTotalMax(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("stir_total.max", wokStirTotalMin(recipe)));
    }

    public Map<String, Object> outcome(RecipeDocument recipe, String path) {
        if (recipe == null || Texts.isBlank(path)) {
            return Map.of();
        }
        Object value = recipe.configuration().get(path);
        if (value instanceof Map<?, ?> map) {
            return Map.copyOf(MapYamlSection.normalizeMap(map));
        }
        return Map.of();
    }

    public List<Map<String, Object>> outputs(Map<String, Object> outcome) {
        if (outcome == null || outcome.isEmpty()) {
            return List.of();
        }
        Object rawOutputs = outcome.get("outputs");
        if (rawOutputs instanceof List<?> list) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    normalized.add(Map.copyOf(MapYamlSection.normalizeMap(map)));
                }
            }
            return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
        }
        if (ItemSourceUtil.parse(outcome.get("source")) == null) {
            return List.of();
        }
        Map<String, Object> singleOutput = new java.util.LinkedHashMap<>(outcome);
        // Direct outcome objects expose their own actions through recipeService.actions(outcome),
        // so we strip them here to avoid double execution when reward delivery also handles
        // per-output actions.
        singleOutput.remove("actions");
        return List.of(Map.copyOf(singleOutput));
    }

    public List<String> actions(Map<String, Object> outcome) {
        if (outcome == null || outcome.isEmpty()) {
            return List.of();
        }
        Object rawActions = outcome.get("actions");
        if (!(rawActions instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        for (Object value : list) {
            if (value != null) {
                actions.add(String.valueOf(value));
            }
        }
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
    }

    public int compareWokStirRule(String stirRule, int actualValue) {
        if (Texts.isBlank(stirRule)) {
            return Integer.compare(actualValue, 0);
        }
        String normalized = stirRule.trim();
        if (normalized.contains("-")) {
            String[] range = normalized.split("-", 2);
            int min = parseInteger(range.length >= 1 ? range[0] : "0", 0);
            int max = parseInteger(range.length >= 2 ? range[1] : range[0], min);
            if (min > max) {
                int swap = min;
                min = max;
                max = swap;
            }
            if (actualValue < min) {
                return -1;
            }
            if (actualValue > max) {
                return 1;
            }
            return 0;
        }
        int expected = parseInteger(normalized, 0);
        return Integer.compare(actualValue, expected);
    }

    private RecipeDocument findByInput(Collection<RecipeDocument> recipes, String inputSource, Player player) {
        if (recipes == null || recipes.isEmpty() || Texts.isBlank(inputSource)) {
            return null;
        }
        ItemSource expected = ItemSourceUtil.parse(inputSource);
        if (expected == null) {
            return null;
        }
        for (RecipeDocument recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            ItemSource configured = parsedSourceCache.computeIfAbsent(recipe,
                    r -> ItemSourceUtil.parse(r.configuration().getString("input.source", "")));
            if (configured == null || !ItemSourceUtil.matches(configured, expected)) {
                continue;
            }
            String permission = recipe.configuration().getString("permission", "");
            if (player != null && Texts.isNotBlank(permission) && !player.hasPermission(permission)) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    public void clearCaches() {
        parsedSourceCache.clear();
    }

    private List<Map<String, Object>> mapList(List<Map<?, ?>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            result.add(Map.copyOf(MapYamlSection.normalizeMap(entry)));
        }
        return List.copyOf(result);
    }

    private int parseInteger(String value, int fallback) {
        if (Texts.isBlank(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception _) {
            return fallback;
        }
    }
}
