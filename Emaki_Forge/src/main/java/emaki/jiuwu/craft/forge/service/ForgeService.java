package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ForgeService {

    private static final String DEFAULT_ACTION_FAILURE_KEY = "forge.error.action_failed";

    public record GuiItems(ItemStack targetItem,
                           Map<Integer, ItemStack> blueprints,
                           Map<Integer, ItemStack> requiredMaterials,
                           Map<Integer, ItemStack> optionalMaterials) {
        public List<ItemStack> blueprintList() {
            return new ArrayList<>(blueprints.values());
        }
    }

    public record ValidationResult(boolean success, String errorKey, Map<String, Object> replacements) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null, Map.of());
        }

        public static ValidationResult fail(String errorKey) {
            return new ValidationResult(false, errorKey, Map.of());
        }

        public static ValidationResult fail(String errorKey, Map<String, Object> replacements) {
            return new ValidationResult(false, errorKey, replacements == null ? Map.of() : replacements);
        }
    }

    public record RecipeMatch(Recipe recipe, String errorKey, Map<String, Object> replacements) {
    }

    public static final class ForgeResult {
        private boolean success;
        private String errorKey;
        private Map<String, Object> replacements = Map.of();
        private ItemStack resultItem;
        private String quality;
        private double multiplier = 1D;
        private String actionFailureReason;

        public boolean success() {
            return success;
        }

        public String errorKey() {
            return errorKey;
        }

        public Map<String, Object> replacements() {
            return replacements;
        }

        public ItemStack resultItem() {
            return resultItem;
        }

        public String quality() {
            return quality;
        }

        public double multiplier() {
            return multiplier;
        }

        public String actionFailureReason() {
            return actionFailureReason;
        }
    }

    private record MaterialPoolEntry(String materialId, ForgeMaterial material, ItemStack item, int remaining) {
    }

    private record NormalizedGuiItems(ItemStack targetItem,
                                      List<ItemStack> blueprints,
                                      Map<String, ItemStack> requiredMaterials,
                                      Map<String, ItemStack> optionalMaterials) {
    }

    private final EmakiForgePlugin plugin;
    private final ForgeResultItemFactory resultItemFactory;
    private final ForgeActionCoordinator actionCoordinator;

    public ForgeService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.resultItemFactory = new ForgeResultItemFactory(plugin);
        this.actionCoordinator = new ForgeActionCoordinator(plugin, resultItemFactory);
    }

    public RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems) {
        ValidationResult firstError = null;
        List<Recipe> recipes = new ArrayList<>(plugin.recipeLoader().all().values());
        recipes.sort(Comparator.comparing(recipe -> Texts.lower(recipe.id())));
        for (Recipe recipe : recipes) {
            ValidationResult validation = canForge(player, recipe, guiItems);
            if (validation.success()) {
                return new RecipeMatch(recipe, null, Map.of());
            }
            if (recipe.targetItemSource() != null
                && ("forge.error.no_target_item".equals(validation.errorKey())
                || "forge.error.invalid_target_item".equals(validation.errorKey()))) {
                continue;
            }
            if (firstError == null) {
                firstError = validation;
            }
        }
        return new RecipeMatch(null,
            firstError == null ? "forge.error.no_recipe" : firstError.errorKey(),
            firstError == null ? Map.of() : firstError.replacements());
    }

    public ValidationResult canForge(Player player, Recipe recipe, GuiItems guiItems) {
        if (recipe == null) {
            return ValidationResult.fail("forge.error.no_recipe");
        }
        NormalizedGuiItems normalized = normalizeMaterialInputs(recipe, guiItems);
        if (normalized == null) {
            return ValidationResult.fail("forge.error.material_not_allowed");
        }
        AppConfig config = plugin.appConfig();
        if (recipe.requiresPermission()
            && !(config.opBypass() && player.isOp())
            && !player.hasPermission(recipe.permission())) {
            return ValidationResult.fail("forge.error.permission_denied");
        }
        if (!recipe.conditions().isEmpty()) {
            boolean conditionsPassed = ConditionEvaluator.evaluate(
                recipe.conditions(),
                recipe.conditionType(),
                recipe.conditionRequiredCount(),
                text -> replacePlaceholders(player, text),
                config.invalidAsFailure()
            );
            if (!conditionsPassed) {
                return ValidationResult.fail("forge.error.condition_not_met");
            }
        }
        if (recipe.targetItemSource() != null) {
            if (normalized.targetItem() == null) {
                return ValidationResult.fail("forge.error.no_target_item");
            }
            if (!plugin.itemIdentifierService().matchesSource(normalized.targetItem(), recipe.targetItemSource())) {
                return ValidationResult.fail("forge.error.invalid_target_item");
            }
        }
        ValidationResult blueprintValidation = validateBlueprints(recipe, normalized.blueprints());
        if (!blueprintValidation.success()) {
            return blueprintValidation;
        }
        ValidationResult requiredValidation = validateRequiredMaterials(recipe, normalized.requiredMaterials());
        if (!requiredValidation.success()) {
            return requiredValidation;
        }
        return validateOptionalMaterials(recipe, normalized.optionalMaterials(), recipe.forgeCapacity());
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player, Recipe recipe, GuiItems guiItems) {
        ForgeResult result = new ForgeResult();
        QualitySettings settings = plugin.appConfig().qualitySettings();
        ValidationResult validation = canForge(player, recipe, guiItems);
        if (!validation.success()) {
            result.errorKey = validation.errorKey();
            result.replacements = validation.replacements();
            return CompletableFuture.completedFuture(result);
        }
        if (normalizeMaterialInputs(recipe, guiItems) == null) {
            result.errorKey = "forge.error.material_not_allowed";
            return CompletableFuture.completedFuture(result);
        }
        return actionCoordinator.executePhase(player, recipe, guiItems, "pre", null, null, 1D, null, null)
            .thenApply(preBatch -> {
                if (!preBatch.success()) {
                    return buildActionFailure(player, recipe, guiItems, result, preBatch);
                }
                QualitySettings.QualityTier qualityTier = rollQuality(player.getUniqueId(), recipe);
                result.quality = qualityTier == null ? settings.defaultTier().name() : qualityTier.name();
                result.multiplier = qualityTier == null ? 1D : qualityTier.multiplier();
                ItemStack resultItem = resultItemFactory.createResultItem(recipe, guiItems, result.multiplier, qualityTier);
                if (resultItem == null) {
                    result.errorKey = "forge.error.item_create";
                    result.replacements = Map.of();
                    actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, result.quality, result.multiplier, result.errorKey, "Unable to create forge result item.");
                    return result;
                }
                giveResult(player, resultItem);
                plugin.playerDataStore().recordCraft(player.getUniqueId(), recipe.id());
                result.success = true;
                result.resultItem = resultItem;
                actionCoordinator.triggerPhase(player, recipe, guiItems, "result", resultItem, result.quality, result.multiplier, null, null);
                actionCoordinator.triggerPhase(player, recipe, guiItems, "success", resultItem, result.quality, result.multiplier, null, null);
                actionCoordinator.triggerQualityActions(player, recipe, guiItems, resultItem, qualityTier, result.quality, result.multiplier);
                return result;
            });
    }

    private ForgeResult buildActionFailure(Player player, Recipe recipe, GuiItems guiItems, ForgeResult result, ActionBatchResult batch) {
        var failure = batch.firstFailure();
        String reason = actionCoordinator.resolveFailureReason(failure);
        result.errorKey = DEFAULT_ACTION_FAILURE_KEY;
        result.actionFailureReason = reason;
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("reason", reason);
        if (failure != null) {
            replacements.put("action", failure.actionId());
            replacements.put("line", failure.lineNumber());
        }
        result.replacements = Map.copyOf(replacements);
        actionCoordinator.triggerPhase(
            player,
            recipe,
            guiItems,
            "failure",
            result.resultItem,
            result.quality,
            result.multiplier,
            result.errorKey,
            reason
        );
        return result;
    }

    private NormalizedGuiItems normalizeMaterialInputs(Recipe recipe, GuiItems guiItems) {
        Map<Integer, ItemStack> requiredMap = guiItems.requiredMaterials() == null ? Map.of() : guiItems.requiredMaterials();
        Map<Integer, ItemStack> optionalMap = guiItems.optionalMaterials() == null ? Map.of() : guiItems.optionalMaterials();
        List<MaterialPoolEntry> pool = new ArrayList<>();
        for (ItemStack itemStack : collectMaterialPool(requiredMap, optionalMap)) {
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                continue;
            }
            ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
            ForgeMaterial material = findMaterialBySource(source);
            if (material == null) {
                return null;
            }
            pool.add(new MaterialPoolEntry(material.id(), material, itemStack.clone(), itemStack.getAmount()));
        }
        Map<String, ItemStack> normalizedRequired = new LinkedHashMap<>();
        int index = 0;
        for (Recipe.RequiredMaterial requiredMaterial : recipe.requiredMaterials()) {
            int needed = requiredMaterial.count();
            for (int poolIndex = 0; poolIndex < pool.size() && needed > 0; poolIndex++) {
                MaterialPoolEntry entry = pool.get(poolIndex);
                if (!requiredMaterial.id().equals(entry.materialId()) || entry.remaining() <= 0) {
                    continue;
                }
                int take = Math.min(needed, entry.remaining());
                ItemStack clone = entry.item().clone();
                clone.setAmount(take);
                normalizedRequired.put("req_" + index++, clone);
                pool.set(poolIndex, new MaterialPoolEntry(entry.materialId(), entry.material(), entry.item(), entry.remaining() - take));
                needed -= take;
            }
        }
        Map<String, ItemStack> normalizedOptional = new LinkedHashMap<>();
        index = 0;
        for (MaterialPoolEntry entry : pool) {
            if (entry.remaining() <= 0) {
                continue;
            }
            ItemStack clone = entry.item().clone();
            clone.setAmount(entry.remaining());
            normalizedOptional.put("opt_" + index++, clone);
        }
        return new NormalizedGuiItems(guiItems.targetItem(), guiItems.blueprintList(), normalizedRequired, normalizedOptional);
    }

    private List<ItemStack> collectMaterialPool(Map<Integer, ItemStack> requiredMap, Map<Integer, ItemStack> optionalMap) {
        List<ItemStack> pool = new ArrayList<>();
        pool.addAll(requiredMap.values());
        pool.addAll(optionalMap.values());
        return pool;
    }

    private ValidationResult validateBlueprints(Recipe recipe, List<ItemStack> blueprintItems) {
        if (recipe.blueprintRequirements().isEmpty()) {
            return ValidationResult.ok();
        }
        Map<String, Integer> available = new LinkedHashMap<>();
        for (ItemStack itemStack : blueprintItems) {
            if (itemStack == null) {
                continue;
            }
            ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
            for (Blueprint blueprint : plugin.blueprintLoader().all().values()) {
                if (ItemSourceUtil.matches(source, blueprint.source())) {
                    available.merge(blueprint.id(), itemStack.getAmount(), Integer::sum);
                }
            }
        }
        Map<String, Integer> reserved = new LinkedHashMap<>();
        for (Recipe.BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if ("all_of".equals(Texts.lower(requirement.requirementMode()))) {
                for (Recipe.BlueprintOption option : requirement.blueprintOptions()) {
                    if (countMatchingBlueprints(option.selector(), available) < option.count()) {
                        return ValidationResult.fail("blueprint.count_not_enough");
                    }
                    reserveBlueprints(option.selector(), option.count(), available, reserved);
                }
            } else {
                Recipe.BlueprintOption satisfied = null;
                for (Recipe.BlueprintOption option : requirement.blueprintOptions()) {
                    if (countMatchingBlueprints(option.selector(), available) >= option.count()) {
                        satisfied = option;
                        break;
                    }
                }
                if (satisfied == null) {
                    return ValidationResult.fail("blueprint.count_not_enough");
                }
                reserveBlueprints(satisfied.selector(), satisfied.count(), available, reserved);
            }
        }
        return ValidationResult.ok();
    }

    private int countMatchingBlueprints(Map<String, Object> selector, Map<String, Integer> available) {
        String kind = Texts.lower(selector.get("kind"));
        String value = Texts.toStringSafe(selector.get("value"));
        if ("id".equals(kind)) {
            return available.getOrDefault(value, 0);
        }
        int total = 0;
        if ("tag".equals(kind)) {
            for (Blueprint blueprint : plugin.blueprintLoader().getByTag(value)) {
                total += available.getOrDefault(blueprint.id(), 0);
            }
        }
        return total;
    }

    private void reserveBlueprints(Map<String, Object> selector,
                                   int count,
                                   Map<String, Integer> available,
                                   Map<String, Integer> reserved) {
        String kind = Texts.lower(selector.get("kind"));
        String value = Texts.toStringSafe(selector.get("value"));
        int remaining = count;
        if ("id".equals(kind)) {
            int amount = Math.min(remaining, available.getOrDefault(value, 0));
            reserved.merge(value, amount, Integer::sum);
            available.put(value, available.getOrDefault(value, 0) - amount);
            return;
        }
        if ("tag".equals(kind)) {
            for (Blueprint blueprint : plugin.blueprintLoader().getByTag(value)) {
                if (remaining <= 0) {
                    break;
                }
                int availableAmount = available.getOrDefault(blueprint.id(), 0);
                int reserve = Math.min(remaining, availableAmount);
                if (reserve <= 0) {
                    continue;
                }
                reserved.merge(blueprint.id(), reserve, Integer::sum);
                available.put(blueprint.id(), availableAmount - reserve);
                remaining -= reserve;
            }
        }
    }

    private ValidationResult validateRequiredMaterials(Recipe recipe, Map<String, ItemStack> requiredMaterials) {
        Map<String, Integer> materialCounts = new LinkedHashMap<>();
        for (ItemStack itemStack : requiredMaterials.values()) {
            ForgeMaterial material = findMaterialBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (material != null) {
                materialCounts.merge(material.id(), itemStack.getAmount(), Integer::sum);
            }
        }
        for (Recipe.RequiredMaterial requiredMaterial : recipe.requiredMaterials()) {
            if (materialCounts.getOrDefault(requiredMaterial.id(), 0) < requiredMaterial.count()) {
                return ValidationResult.fail("material.count_not_enough");
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateOptionalMaterials(Recipe recipe, Map<String, ItemStack> optionalMaterials, int maxCapacity) {
        Recipe.OptionalMaterialsConfig optionalConfig = recipe.optionalMaterials();
        if (!optionalConfig.enabled()) {
            for (ItemStack itemStack : optionalMaterials.values()) {
                if (itemStack != null && itemStack.getAmount() > 0) {
                    return ValidationResult.fail("forge.error.material_not_allowed");
                }
            }
            return ValidationResult.ok();
        }
        int totalCapacity = 0;
        int capacityBonus = 0;
        int totalItems = 0;
        for (ItemStack itemStack : optionalMaterials.values()) {
            ForgeMaterial material = findMaterialBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (material == null) {
                continue;
            }
            if (!optionalConfig.whitelist().isEmpty() && !optionalConfig.whitelist().contains(material.id())) {
                return ValidationResult.fail("forge.error.material_not_allowed");
            }
            if (optionalConfig.blacklist().contains(material.id())) {
                return ValidationResult.fail("forge.error.material_blacklisted");
            }
            totalCapacity += material.effectiveCapacityCost() * itemStack.getAmount();
            capacityBonus += material.forgeCapacityBonus() * itemStack.getAmount();
            totalItems += itemStack.getAmount();
        }
        if (optionalConfig.maxCount() > 0 && totalItems > optionalConfig.maxCount()) {
            return ValidationResult.fail("forge.error.material_count_exceeded", Map.of("current", totalItems, "max", optionalConfig.maxCount()));
        }
        int effectiveMaxCapacity = maxCapacity + capacityBonus;
        if (effectiveMaxCapacity > 0 && totalCapacity > effectiveMaxCapacity) {
            return ValidationResult.fail("forge.error.capacity_exceeded", Map.of("current", totalCapacity, "max", effectiveMaxCapacity));
        }
        return ValidationResult.ok();
    }

    private QualitySettings.QualityTier rollQuality(UUID playerId, Recipe recipe) {
        QualitySettings settings = plugin.appConfig().qualitySettings();
        Recipe.QualityConfig qualityConfig = recipe.quality();
        boolean guaranteeEnabled = qualityConfig.enabled() && qualityConfig.guaranteeEnabled();
        int guaranteeThreshold = qualityConfig.guaranteeAttempts();
        String guaranteeMinimum = qualityConfig.guaranteeMinimum();
        if (!guaranteeEnabled && settings.guaranteeEnabled()) {
            guaranteeEnabled = true;
            guaranteeThreshold = settings.guaranteeThreshold();
            guaranteeMinimum = settings.minimumTier().name();
        }
        if (guaranteeEnabled) {
            int counter = plugin.playerDataStore().guaranteeCounter(playerId, recipe.id());
            if (counter >= guaranteeThreshold - 1) {
                plugin.playerDataStore().resetGuaranteeCounter(playerId, recipe.id());
                QualitySettings.QualityTier minimumTier = findMinimumTier(qualityConfig.customPool(), guaranteeMinimum);
                return minimumTier == null ? settings.minimumTier() : minimumTier;
            }
        }
        List<QualitySettings.QualityTier> tiers = new ArrayList<>();
        if (qualityConfig.enabled() && !qualityConfig.customPool().isEmpty()) {
            for (String entry : qualityConfig.customPool()) {
                QualitySettings.QualityTier tier = QualitySettings.QualityTier.fromString(entry);
                if (tier != null) {
                    tiers.add(tier);
                }
            }
        }
        if (tiers.isEmpty()) {
            tiers.addAll(settings.tiers());
        }
        List<Randoms.Weighted<QualitySettings.QualityTier>> weighted = new ArrayList<>();
        for (QualitySettings.QualityTier tier : tiers) {
            weighted.add(new Randoms.Weighted<>(tier, tier.weight()));
        }
        QualitySettings.QualityTier rolled = Randoms.weightedRandom(weighted);
        if (rolled == null) {
            rolled = settings.defaultTier();
        }
        if (guaranteeEnabled) {
            QualitySettings.QualityTier minimumTier = findMinimumTier(qualityConfig.customPool(), guaranteeMinimum);
            if (minimumTier == null) {
                minimumTier = settings.minimumTier();
            }
            if (tierIndex(tiers, rolled) < tierIndex(tiers, minimumTier)) {
                plugin.playerDataStore().incrementGuaranteeCounter(playerId, recipe.id());
            } else {
                plugin.playerDataStore().resetGuaranteeCounter(playerId, recipe.id());
            }
        }
        return rolled;
    }

    private QualitySettings.QualityTier findMinimumTier(List<String> pool, String minimumName) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        QualitySettings.QualityTier first = null;
        for (String entry : pool) {
            QualitySettings.QualityTier tier = QualitySettings.QualityTier.fromString(entry);
            if (tier == null) {
                continue;
            }
            if (first == null) {
                first = tier;
            }
            if (Texts.lower(tier.name()).equals(Texts.lower(minimumName))) {
                return tier;
            }
        }
        return first;
    }

    private int tierIndex(List<QualitySettings.QualityTier> tiers, QualitySettings.QualityTier target) {
        for (int index = 0; index < tiers.size(); index++) {
            if (tiers.get(index).name().equals(target.name())) {
                return index;
            }
        }
        return -1;
    }

    private void consumeItems(Player player, Recipe recipe, GuiItems guiItems) {
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (topInventory == null) {
            return;
        }
        if (recipe.targetItemSource() != null && guiItems.targetItem() != null) {
            removeSimilarAmount(topInventory, guiItems.targetItem(), 1);
        }
        if (!recipe.blueprintRequirements().isEmpty()) {
            Map<String, Integer> available = new LinkedHashMap<>();
            for (ItemStack itemStack : guiItems.blueprintList()) {
                if (itemStack == null) {
                    continue;
                }
                ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
                for (Blueprint blueprint : plugin.blueprintLoader().all().values()) {
                    if (ItemSourceUtil.matches(source, blueprint.source())) {
                        available.merge(blueprint.id(), itemStack.getAmount(), Integer::sum);
                    }
                }
            }
            Map<String, Integer> plan = new LinkedHashMap<>();
            for (Recipe.BlueprintRequirement requirement : recipe.blueprintRequirements()) {
                if ("all_of".equals(Texts.lower(requirement.requirementMode()))) {
                    for (Recipe.BlueprintOption option : requirement.blueprintOptions()) {
                        reserveBlueprints(option.selector(), option.count(), available, plan);
                    }
                } else {
                    for (Recipe.BlueprintOption option : requirement.blueprintOptions()) {
                        if (countMatchingBlueprints(option.selector(), available) >= option.count()) {
                            reserveBlueprints(option.selector(), option.count(), available, plan);
                            break;
                        }
                    }
                }
            }
            for (Map.Entry<String, Integer> entry : plan.entrySet()) {
                Blueprint blueprint = plugin.blueprintLoader().get(entry.getKey());
                if (blueprint != null) {
                    removeSourceAmount(topInventory, blueprint.source(), entry.getValue());
                }
            }
        }
        for (Recipe.RequiredMaterial requiredMaterial : recipe.requiredMaterials()) {
            ForgeMaterial material = plugin.materialLoader().get(requiredMaterial.id());
            if (material != null) {
                removeSourceAmount(topInventory, material.source(), requiredMaterial.count());
            }
        }
        for (ItemStack itemStack : guiItems.optionalMaterials().values()) {
            if (itemStack == null) {
                continue;
            }
            removeSourceAmount(topInventory, plugin.itemIdentifierService().identifyItem(itemStack), itemStack.getAmount());
        }
    }

    private int removeSourceAmount(Inventory inventory, ItemSource source, int amount) {
        if (inventory == null || source == null || amount <= 0) {
            return 0;
        }
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize() && removed < amount; slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || !ItemSourceUtil.matches(plugin.itemIdentifierService().identifyItem(itemStack), source)) {
                continue;
            }
            int take = Math.min(amount - removed, itemStack.getAmount());
            if (take >= itemStack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                itemStack.setAmount(itemStack.getAmount() - take);
                inventory.setItem(slot, itemStack);
            }
            removed += take;
        }
        return removed;
    }

    private void removeSimilarAmount(Inventory inventory, ItemStack itemStack, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || !current.isSimilar(itemStack)) {
                continue;
            }
            if (current.getAmount() <= remaining) {
                remaining -= current.getAmount();
                inventory.setItem(slot, null);
            } else {
                current.setAmount(current.getAmount() - remaining);
                inventory.setItem(slot, current);
                remaining = 0;
            }
        }
    }

    private void giveResult(Player player, ItemStack itemStack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private ForgeMaterial findMaterialBySource(ItemSource source) {
        if (source == null) {
            return null;
        }
        for (ForgeMaterial material : plugin.materialLoader().all().values()) {
            if (ItemSourceUtil.matches(source, material.source())) {
                return material;
            }
        }
        return null;
    }

    public String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        return resultItemFactory.resolveResultItemName(recipe, itemStack);
    }

    private String replacePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text) || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        return Texts.toStringSafe(PlaceholderAPI.setPlaceholders(player, text));
    }
}
