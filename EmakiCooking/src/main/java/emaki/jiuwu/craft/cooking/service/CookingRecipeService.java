package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class CookingRecipeService {

    private final EmakiCookingPlugin plugin;
    private final CookingSettingsService settingsService;
    private final Map<StationType, RecipeIndex> recipeIndexes = new ConcurrentHashMap<>();
    private final Map<String, WokSpec> wokSpecs = new ConcurrentHashMap<>();

    public CookingRecipeService(EmakiCookingPlugin plugin, CookingSettingsService settingsService) {
        this.plugin = plugin;
        this.settingsService = settingsService;
    }

    public RecipeDocument findChoppingBoardRecipe(String inputSource, Player player) {
        return findChoppingBoardRecipe(inputSource, player, null);
    }

    public RecipeDocument findChoppingBoardRecipe(String inputSource, Player player, ItemStack itemStack) {
        return findByInput(StationType.CHOPPING_BOARD, inputSource, player, itemStack);
    }

    public RecipeDocument findGrinderRecipe(String inputSource, Player player) {
        return findGrinderRecipe(inputSource, player, null);
    }

    public RecipeDocument findGrinderRecipe(String inputSource, Player player, ItemStack itemStack) {
        return findByInput(StationType.GRINDER, inputSource, player, itemStack);
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
        return findSteamerRecipe(inputSource, player, null);
    }

    public RecipeDocument findSteamerRecipe(String inputSource, Player player, ItemStack itemStack) {
        return findByInput(StationType.STEAMER, inputSource, player, itemStack);
    }

    public int steamerRequiredSteam(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("required_steam", 0));
    }

    public RecipeDocument findOvenRecipe(String inputSource, Player player) {
        return findOvenRecipe(inputSource, player, null);
    }

    public RecipeDocument findOvenRecipe(String inputSource, Player player, ItemStack itemStack) {
        return findByInput(StationType.OVEN, inputSource, player, itemStack);
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
        return findJuicerRecipe(inputSource, player, null);
    }

    public RecipeDocument findJuicerRecipe(String inputSource, Player player, ItemStack itemStack) {
        return findByInput(StationType.JUICER, inputSource, player, itemStack);
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

    public ItemRequirement juicerContainerRequirement(RecipeDocument recipe) {
        return recipe == null
                ? new ItemRequirement(List.of(), null, "")
                : CookingMatchers.requirement(recipe.configuration(), "container.item_sources", "container.matcher");
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
        if (recipe == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> raw : mapList(recipe.configuration().getMapList("inputs"))) {
            Map<String, Object> input = new LinkedHashMap<>(raw);
            input.put("slot_id", Texts.toStringSafe(input.get("slot_id")).trim().toLowerCase(java.util.Locale.ROOT));
            input.put("count_key", Texts.toStringSafe(input.get("count_key")).trim().toLowerCase(java.util.Locale.ROOT));
            input.put("amount", Math.max(1, Numbers.tryParseInt(input.get("amount"), 1)));
            result.add(Map.copyOf(input));
        }
        return List.copyOf(result);
    }

    public List<CookingInputIngredient> fermentationInputIngredients(RecipeDocument recipe) {
        List<CookingInputIngredient> result = new ArrayList<>();
        for (Map<String, Object> input : fermentationInputs(recipe)) {
            List<String> sources = new ArrayList<>();
            for (Object source : ConfigNodes.asObjectList(input.get("item_sources"))) {
                String value = Texts.toStringSafe(source);
                if (Texts.isNotBlank(value)) {
                    sources.add(value);
                }
            }
            Map<String, Object> matcher = input.get("matcher") instanceof Map<?, ?> map
                    ? MapYamlSection.normalizeMap(map) : Map.of();
            result.add(new CookingInputIngredient(
                    sources.isEmpty() ? "" : sources.get(0),
                    Numbers.tryParseInt(input.get("amount"), 1),
                    Texts.toStringSafe(input.get("slot_id")),
                    Texts.toStringSafe(input.get("count_key")),
                    sources,
                    matcher));
        }
        return List.copyOf(result);
    }

    public List<Map<String, Object>> wokIngredients(RecipeDocument recipe) {
        return recipe == null ? List.of() : wokSpec(recipe).ingredients();
    }

    public int wokHeatLevel(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("heat_level", 0));
    }

    public int wokFaultTolerance(RecipeDocument recipe) {
        return recipe == null ? 0 : Math.max(0, recipe.configuration().getInt("fault_tolerance", 0));
    }

    public boolean canUseRecipe(RecipeDocument recipe, Player player) {
        return canUseRecipe(recipe, player, null);
    }

    private boolean canUseRecipe(RecipeDocument recipe, Player player, ConditionBlock cachedCondition) {
        if (recipe == null) {
            return false;
        }
        String permission = recipe.configuration().getString("permission", "");
        if (player != null && Texts.isNotBlank(permission) && !player.hasPermission(permission)) {
            return false;
        }
        ConditionBlock condition = cachedCondition == null
                ? availabilityCondition(recipe.configuration())
                : cachedCondition;
        if (player != null && condition.configured()) {
            return ConditionEvaluator.evaluate(
                    condition,
                    text -> resolvePlaceholders(player, text),
                    ConditionContext.of(player, null, Map.of("recipeId", recipe.id()))
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
                ConditionContext.of(player, null, Map.of("recipeId", recipe.id()))
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
            if (matchesWokIngredientPrefix(recipe, actualIngredients, player)) {
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

    private RecipeDocument findByInput(StationType stationType,
            String inputSource,
            Player player,
            ItemStack itemStack) {
        if (Texts.isBlank(inputSource)) {
            return null;
        }
        ItemSourceRef expected = ItemSourceUtil.parse(inputSource);
        if (expected == null) {
            return null;
        }
        for (IndexedRecipe indexed : indexFor(stationType).candidates(expected)) {
            if (!canUseRecipe(indexed.recipe(), player, indexed.condition())) {
                continue;
            }
            if (itemStack != null && !itemStack.getType().isAir()
                    && !indexed.requirement().test(itemStack, expected, player)) {
                continue;
            }
            return indexed.recipe();
        }
        return null;
    }

    private RecipeIndex indexFor(StationType stationType) {
        return recipeIndexes.computeIfAbsent(stationType, this::buildIndex);
    }

    private RecipeIndex buildIndex(StationType stationType) {
        Map<ItemSourceRef, List<IndexedRecipe>> bySource = new HashMap<>();
        List<IndexedRecipe> wildcard = new ArrayList<>();
        Collection<RecipeDocument> recipes = recipesFor(stationType);
        int ordinal = 0;
        if (recipes != null) {
            for (RecipeDocument recipe : recipes) {
                if (recipe == null) {
                    continue;
                }
                YamlSection input = recipe.configuration().getSection("input");
                ItemRequirement requirement = CookingMatchers.requirement(input, "item_sources", "matcher");
                if (requirement.empty()) {
                    continue;
                }
                ordinal++;
                IndexedRecipe indexed = new IndexedRecipe(
                        recipe,
                        requirement,
                        availabilityCondition(recipe.configuration()),
                        ordinal);
                if (requirement.sources().isEmpty()) {
                    wildcard.add(indexed);
                    continue;
                }
                for (ItemSourceRef source : requirement.sources()) {
                    bySource.computeIfAbsent(source, _ -> new ArrayList<>()).add(indexed);
                }
            }
        }
        Map<ItemSourceRef, List<IndexedRecipe>> frozen = new HashMap<>(bySource.size());
        for (Map.Entry<ItemSourceRef, List<IndexedRecipe>> entry : bySource.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new RecipeIndex(Map.copyOf(frozen), List.copyOf(wildcard));
    }

    private Collection<RecipeDocument> recipesFor(StationType stationType) {
        return switch (stationType) {
            case CHOPPING_BOARD -> plugin.choppingBoardRecipeLoader().all().values();
            case GRINDER -> plugin.grinderRecipeLoader().all().values();
            case STEAMER -> plugin.steamerRecipeLoader().all().values();
            case OVEN -> plugin.ovenRecipeLoader().all().values();
            case JUICER -> plugin.juicerRecipeLoader().all().values();
            default -> List.of();
        };
    }

    private WokSpec wokSpec(RecipeDocument recipe) {
        return wokSpecs.computeIfAbsent(recipe.id(), _ -> buildWokSpec(recipe));
    }

    private WokSpec buildWokSpec(RecipeDocument recipe) {
        List<Map<String, Object>> ingredients = mapList(recipe.configuration().getMapList("ingredients"));
        List<ItemRequirement> requirements = new ArrayList<>(ingredients.size());
        List<Integer> amounts = new ArrayList<>(ingredients.size());
        for (Map<String, Object> ingredient : ingredients) {
            requirements.add(CookingMatchers.requirement(ingredient, "item_sources", "matcher"));
            amounts.add(Math.max(1, Numbers.tryParseInt(ingredient.get("amount"), 1)));
        }
        return new WokSpec(List.copyOf(ingredients), List.copyOf(requirements), List.copyOf(amounts));
    }

    private boolean matchesWokIngredientPrefix(RecipeDocument recipe, List<WokIngredientInput> actualIngredients, Player player) {
        WokSpec spec = wokSpec(recipe);
        List<Map<String, Object>> expectedIngredients = spec.ingredients();
        if (expectedIngredients.isEmpty() || actualIngredients.size() > expectedIngredients.size()) {
            return false;
        }
        for (int index = 0; index < actualIngredients.size(); index++) {
            WokIngredientInput actual = actualIngredients.get(index);
            if (actual == null || Texts.isBlank(actual.source())) {
                return false;
            }
            ItemRequirement requirement = spec.requirements().get(index);
            int expectedAmount = spec.amounts().get(index);
            if (requirement.empty() || !requirement.matchesSource(ItemSourceUtil.parse(actual.source()))) {
                return false;
            }
            if (actual.itemStack() != null && !actual.itemStack().getType().isAir()
                    && !requirement.test(actual.itemStack(), ItemSourceUtil.parse(actual.source()), player)) {
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

    public void clearCaches() {
        recipeIndexes.clear();
        wokSpecs.clear();
    }

    private record IndexedRecipe(RecipeDocument recipe,
            ItemRequirement requirement,
            ConditionBlock condition,
            int ordinal) {
    }

    private record WokSpec(List<Map<String, Object>> ingredients,
            List<ItemRequirement> requirements,
            List<Integer> amounts) {
    }

    private static final class RecipeIndex {

        private final Map<ItemSourceRef, List<IndexedRecipe>> bySource;
        private final List<IndexedRecipe> wildcard;

        private RecipeIndex(Map<ItemSourceRef, List<IndexedRecipe>> bySource, List<IndexedRecipe> wildcard) {
            this.bySource = bySource;
            this.wildcard = wildcard;
        }

        private List<IndexedRecipe> candidates(ItemSourceRef source) {
            List<IndexedRecipe> matched = bySource.get(source);
            if (wildcard.isEmpty()) {
                return matched == null ? List.of() : matched;
            }
            if (matched == null || matched.isEmpty()) {
                return wildcard;
            }
            List<IndexedRecipe> merged = new ArrayList<>(matched.size() + wildcard.size());
            merged.addAll(matched);
            merged.addAll(wildcard);
            merged.sort(Comparator.comparingInt(IndexedRecipe::ordinal));
            return merged;
        }
    }

    public boolean satisfiesPreviousStep(RecipeDocument recipe, ItemStack itemStack) {
        if (recipe == null || itemStack == null || itemStack.getType().isAir()) {
            return true;
        }
        String requiredStep = recipe.configuration().getString("requires_previous_step", "");
        if (Texts.isBlank(requiredStep)) {
            return true;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(plugin, "cooking_history");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String history = pdc.getOrDefault(key, PersistentDataType.STRING, "");
        return history.contains(requiredStep);
    }

    public void writeProcessingHistory(ItemStack itemStack, String recipeId) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(recipeId)) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "cooking_history");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String existing = pdc.getOrDefault(key, PersistentDataType.STRING, "");
        String updated = existing.isEmpty() ? recipeId : existing + "," + recipeId;
        pdc.set(key, PersistentDataType.STRING, updated);
        boolean committed = itemStack.setItemMeta(meta);
        if (plugin.debugLogger() != null) {
            plugin.debugLogger().log("pdc", (UUID) null, "pdc.cooking_history", Map.of(
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
                resolved = Texts.toStringSafe(PlaceholderAPI.setPlaceholders(player, resolved));
            } catch (Exception | NoClassDefFoundError _) {
            }
        }
        return resolved;
    }

    private Map<String, String> playerVariables(Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player_name", player.getName());
        values.put("player_level", Integer.toString(player.getLevel()));
        values.put("player_exp", Float.toString(player.getExp()));
        values.put("player_food", Integer.toString(player.getFoodLevel()));
        values.put("player_health", Double.toString(player.getHealth()));
        values.put("player_world", player.getWorld() == null ? "" : player.getWorld().getName());
        return values;
    }

    public record WokIngredientInput(String source, int amount, ItemStack itemStack) {

        public WokIngredientInput {
            source = Texts.toStringSafe(source);
            amount = Math.max(1, amount);
        }

        public WokIngredientInput(String source, int amount) {
            this(source, amount, null);
        }
    }
}
