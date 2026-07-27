package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
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

    public int choppingInputAmount(RecipeDocument recipe) {
        return recipe == null ? 1 : Math.max(1, recipe.configuration().getInt("input.amount", 1));
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
        return outputs(outcome(recipe, "result.success"));
    }

    public List<String> actions(RecipeDocument recipe) {
        return actions(outcome(recipe, "result.success"));
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

    public RecipeDocument findOvenRecipe(String inputSource, Player player) {
        return findByInput(plugin.ovenRecipeLoader().all().values(), inputSource, player);
    }

    public int ovenBakeTimeSeconds(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("bake_time_seconds", 0));
    }

    public int ovenPerfectHeatMin(RecipeDocument recipe) {
        return recipe == null ? settingsService.ovenHeatMin()
                : Math.max(0, recipe.configuration().getInt("baking.perfect_heat.min", settingsService.ovenHeatMin()));
    }

    public int ovenPerfectHeatMax(RecipeDocument recipe) {
        return recipe == null ? settingsService.ovenHeatMax()
                : Math.max(ovenPerfectHeatMin(recipe), recipe.configuration().getInt("baking.perfect_heat.max", settingsService.ovenHeatMax()));
    }

    public double ovenPerfectRequiredRatio(RecipeDocument recipe) {
        if (recipe == null) {
            return 1.0D;
        }
        double value = recipe.configuration().getDouble("baking.perfect_required_ratio", 1.0D);
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public int ovenOverbakeSeconds(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("baking.overbake_seconds", 0));
    }

    public Map<String, Object> ovenOutcomeForStage(RecipeDocument recipe, OvenBakeStage stage) {
        if (stage == OvenBakeStage.PERFECT) {
            return outcome(recipe, "result.perfect");
        }
        if (stage == OvenBakeStage.OVERBAKED) {
            return outcome(recipe, "result.overbaked");
        }
        return outcome(recipe, "result.success");
    }

    public RecipeDocument findJuicerRecipe(String inputSource, Player player) {
        return findByInput(plugin.juicerRecipeLoader().all().values(), inputSource, player);
    }

    public int juicerPressesRequired(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("presses_required", 0));
    }

    public boolean juicerHasFluidMode(RecipeDocument recipe) {
        return Texts.isNotBlank(juicerFluidId(recipe));
    }

    public String juicerFluidId(RecipeDocument recipe) {
        return recipe == null ? "" : Texts.toStringSafe(recipe.configuration().getString("fluid.id", "")).trim();
    }

    public String juicerFluidDisplayName(RecipeDocument recipe) {
        if (recipe == null) {
            return "";
        }
        String displayName = recipe.configuration().getString("fluid.display_name", "");
        return Texts.isBlank(displayName) ? juicerFluidId(recipe) : displayName;
    }

    public int juicerFluidAmountMl(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("fluid.amount_ml", 0));
    }

    public int juicerServingMl(RecipeDocument recipe) {
        return recipe == null ? settingsService.juicerDefaultServingMl()
                : Math.max(1, recipe.configuration().getInt("container.serving_ml", settingsService.juicerDefaultServingMl()));
    }

    public RecipeDocument findJuicerRecipeByFluidId(String fluidId, Player player) {
        if (Texts.isBlank(fluidId)) {
            return null;
        }
        for (RecipeDocument recipe : plugin.juicerRecipeLoader().all().values()) {
            if (recipe == null || !fluidId.equalsIgnoreCase(juicerFluidId(recipe)) || !canUseRecipe(recipe, player)) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    public List<ItemSource> juicerContainerSources(RecipeDocument recipe) {
        if (recipe == null) {
            return List.of();
        }
        return parseItemSources(recipe.configuration().get("container.item_sources"));
    }

    public Collection<RecipeDocument> fermentationBarrelRecipes() {
        Collection<RecipeDocument> recipes = plugin.fermentationBarrelRecipeLoader().all().values();
        return recipes == null || recipes.isEmpty() ? List.of() : List.copyOf(recipes);
    }

    public RecipeDocument fermentationBarrelRecipeById(String recipeId) {
        return Texts.isBlank(recipeId) ? null : plugin.fermentationBarrelRecipeLoader().get(recipeId);
    }

    public int fermentationTimeSeconds(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("fermentation_time_seconds", 0));
    }

    public double fermentationEarlyMinProgressRatio(RecipeDocument recipe) {
        if (recipe == null || outcome(recipe, "result.early").isEmpty()) {
            return -1.0D;
        }
        double value = recipe.configuration().getDouble("fermentation.early_collect.min_progress_ratio", 1.0D);
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public int fermentationOverTimeSeconds(RecipeDocument recipe) {
        if (recipe == null || outcome(recipe, "result.over").isEmpty()) {
            return 0;
        }
        return Math.max(0, recipe.configuration().getInt("fermentation.over_time_seconds", 0));
    }

    public Map<String, Object> fermentationOutcomeForStage(RecipeDocument recipe, FermentationStage stage) {
        if (stage == FermentationStage.EARLY) {
            return outcome(recipe, "result.early");
        }
        if (stage == FermentationStage.OVER) {
            return outcome(recipe, "result.over");
        }
        return outcome(recipe, "result.success");
    }

    public List<Map<String, Object>> fermentationInputs(RecipeDocument recipe) {
        return recipe == null ? List.of() : mapList(recipe.configuration().getMapList("inputs"));
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

    public boolean canUseRecipe(RecipeDocument recipe, Player player) {
        if (recipe == null) {
            return false;
        }
        String permission = recipe.configuration().getString("permission", "");
        if (player != null && Texts.isNotBlank(permission) && !player.hasPermission(permission)) {
            return false;
        }
        ConditionBlock condition = availabilityCondition(recipe.configuration());
        if (player != null && condition.configured()) {
            return ConditionEvaluator.evaluate(
                    condition,
                    text -> resolvePlaceholders(player, text),
                    ConditionContext.of(player, null, java.util.Map.of("recipeId", recipe.id()))
            );
        }
        return true;
    }

    private ConditionBlock availabilityCondition(YamlSection configuration) {
        if (configuration == null) {
            return ConditionBlock.empty();
        }
        YamlSection section = configuration.getSection("availability_condition");
        if (section != null && !section.isEmpty()) {
            return ConditionBlock.fromConfig(section, true, false);
        }
        return ConditionBlock.empty();
    }


    public boolean hasCompletionCondition(RecipeDocument recipe) {
        if (recipe == null) {
            return false;
        }
        YamlSection section = recipe.configuration().getSection("condition");
        return section != null && !section.isEmpty();
    }

    public boolean completionConditionPasses(RecipeDocument recipe, Player player) {
        if (recipe == null) {
            return true;
        }
        YamlSection section = recipe.configuration().getSection("condition");
        if (section == null || section.isEmpty()) {
            return true;
        }
        ConditionBlock condition = ConditionBlock.fromConfig(section, true, false);
        if (!condition.configured() || player == null) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                condition,
                text -> resolvePlaceholders(player, text),
                ConditionContext.of(player, null, java.util.Map.of("recipeId", recipe.id()))
        );
    }

    public List<String> completionConditionActions(RecipeDocument recipe, boolean passed) {
        if (recipe == null) {
            return List.of();
        }
        YamlSection section = recipe.configuration().getSection("condition");
        if (section == null || section.isEmpty()) {
            return List.of();
        }
        ConditionBlock condition = ConditionBlock.fromConfig(section, true, false);
        return passed ? condition.passActions() : condition.failActions();
    }

    public boolean completionConditionBlocksOutput(RecipeDocument recipe) {
        if (recipe == null) {
            return false;
        }
        YamlSection section = recipe.configuration().getSection("condition");
        if (section == null || section.isEmpty()) {
            return false;
        }
        return ConditionBlock.fromConfig(section, true, false).blockOutput();
    }

    public boolean canAcceptWokIngredientPrefix(List<WokIngredientInput> actualIngredients, Player player, int heatLevel) {
        if (actualIngredients == null || actualIngredients.isEmpty()) {
            return false;
        }
        for (RecipeDocument recipe : wokRecipes()) {
            if (!canUseRecipe(recipe, player)) {
                continue;
            }
            if (wokHeatLevel(recipe) > 0 && wokHeatLevel(recipe) != heatLevel) {
                continue;
            }
            if (matchesWokIngredientPrefix(recipe, actualIngredients)) {
                return true;
            }
        }
        return false;
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
        if (!(rawOutputs instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                normalized.add(Map.copyOf(MapYamlSection.normalizeMap(map)));
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
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
                    r -> ItemSourceUtil.parse(r.configuration().get("input.item_sources")));
            if (configured == null || !ItemSourceUtil.matches(configured, expected)) {
                continue;
            }
            if (!canUseRecipe(recipe, player)) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    private boolean matchesWokIngredientPrefix(RecipeDocument recipe, List<WokIngredientInput> actualIngredients) {
        List<Map<String, Object>> expectedIngredients = wokIngredients(recipe);
        if (expectedIngredients.isEmpty() || actualIngredients.size() > expectedIngredients.size()) {
            return false;
        }
        for (int index = 0; index < actualIngredients.size(); index++) {
            WokIngredientInput actual = actualIngredients.get(index);
            if (actual == null || Texts.isBlank(actual.source())) {
                return false;
            }
            Map<String, Object> expected = expectedIngredients.get(index);
            String expectedSource = firstSourceShorthand(expected.get("item_sources"));
            int expectedAmount = Math.max(1, Numbers.tryParseInt(expected.get("amount"), 1));
            if (!ItemSourceUtil.matches(ItemSourceUtil.parse(expectedSource), ItemSourceUtil.parse(actual.source()))) {
                return false;
            }
            if (actual.amount() > expectedAmount) {
                return false;
            }
            if (index < actualIngredients.size() - 1 && actual.amount() != expectedAmount) {
                return false;
            }
        }
        return true;
    }

    private String firstSourceShorthand(Object raw) {
        ItemSource source = ItemSourceUtil.parse(raw);
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
    }

    private List<ItemSource> parseItemSources(Object raw) {
        List<ItemSource> sources = new ArrayList<>();
        for (Object token : emaki.jiuwu.craft.corelib.config.ConfigNodes.asObjectList(raw)) {
            ItemSource source = ItemSourceUtil.parse(token);
            if (source != null) {
                sources.add(source);
            }
        }
        return sources.isEmpty() ? List.of() : List.copyOf(sources);
    }

    public void clearCaches() {
        parsedSourceCache.clear();
    }

    public boolean satisfiesPreviousStep(RecipeDocument recipe, org.bukkit.inventory.ItemStack itemStack) {
        if (recipe == null || itemStack == null || itemStack.getType().isAir()) {
            return true;
        }
        String requiredStep = recipe.configuration().getString("requires_previous_step", "");
        if (Texts.isBlank(requiredStep)) {
            return true;
        }
        org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "cooking_history");
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String history = pdc.getOrDefault(key, org.bukkit.persistence.PersistentDataType.STRING, "");
        return history.contains(requiredStep);
    }

    public void writeProcessingHistory(org.bukkit.inventory.ItemStack itemStack, String recipeId) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(recipeId)) {
            return;
        }
        org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "cooking_history");
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String existing = pdc.getOrDefault(key, org.bukkit.persistence.PersistentDataType.STRING, "");
        String updated = existing.isEmpty() ? recipeId : existing + "," + recipeId;
        pdc.set(key, org.bukkit.persistence.PersistentDataType.STRING, updated);
        boolean committed = itemStack.setItemMeta(meta);
        if (plugin.debugLogger() != null) {
            plugin.debugLogger().log("pdc", (java.util.UUID) null, "pdc.cooking_history", Map.of(
                    "item", itemStack.getType(),
                    "amount", itemStack.getAmount(),
                    "key", key,
                    "before", existing,
                    "after", updated,
                    "recipe", recipeId,
                    "committed", committed
            ));
        }
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
        return Numbers.tryParseInt(value, fallback);
    }

    private String resolvePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text)) {
            return text;
        }
        String resolved = text;
        if (resolved.indexOf('{') >= 0) {
            for (Map.Entry<String, String> entry : playerVariables(player).entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        if (resolved.indexOf('%') >= 0 && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                resolved = Texts.toStringSafe(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, resolved));
            } catch (Exception | NoClassDefFoundError _) {
            }
        }
        return resolved;
    }

    private Map<String, String> playerVariables(Player player) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("player_name", player.getName());
        values.put("player_level", Integer.toString(player.getLevel()));
        values.put("player_exp", Float.toString(player.getExp()));
        values.put("player_food", Integer.toString(player.getFoodLevel()));
        values.put("player_health", Double.toString(player.getHealth()));
        values.put("player_world", player.getWorld() == null ? "" : player.getWorld().getName());
        return values;
    }

    public record WokIngredientInput(String source, int amount) {

        public WokIngredientInput {
            source = Texts.toStringSafe(source);
            amount = Math.max(1, amount);
        }
    }
}
