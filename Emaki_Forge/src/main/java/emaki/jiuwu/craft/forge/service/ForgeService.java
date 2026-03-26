package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionStepResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
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
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ForgeService {

    private static final String DEFAULT_OPERATION_FAILURE_KEY = "forge.error.operation_failed";

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
        private String operationFailureReason;

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

        public String operationFailureReason() {
            return operationFailureReason;
        }
    }

    private record MaterialPoolEntry(String materialId, ForgeMaterial material, ItemStack item, int remaining) {
    }

    private record NormalizedGuiItems(ItemStack targetItem,
                                      List<ItemStack> blueprints,
                                      Map<String, ItemStack> requiredMaterials,
                                      Map<String, ItemStack> optionalMaterials) {
    }

    private record MaterialContribution(ForgeMaterial material,
                                        int amount,
                                        int slot,
                                        String category,
                                        int sequence,
                                        ItemSource source) {
    }

    private final EmakiForgePlugin plugin;

    public ForgeService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
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
        return executePhase(player, recipe, guiItems, "pre", null, null, 1D, null, null)
            .thenApply(preBatch -> {
                if (!preBatch.success()) {
                    return buildActionFailure(player, recipe, guiItems, result, preBatch);
                }
                QualitySettings.QualityTier qualityTier = rollQuality(player.getUniqueId(), recipe);
                result.quality = qualityTier == null ? settings.defaultTier().name() : qualityTier.name();
                result.multiplier = qualityTier == null ? 1D : qualityTier.multiplier();
                ItemStack resultItem = createResultItem(recipe, guiItems, result.multiplier, qualityTier);
                if (resultItem == null) {
                    result.errorKey = "forge.error.item_create";
                    result.replacements = Map.of();
                    triggerPhase(player, recipe, guiItems, "failure", null, result.quality, result.multiplier, result.errorKey, "Unable to create forge result item.");
                    return result;
                }
                giveResult(player, resultItem);
                plugin.playerDataStore().recordCraft(player.getUniqueId(), recipe.id());
                result.success = true;
                result.resultItem = resultItem;
                triggerPhase(player, recipe, guiItems, "result", resultItem, result.quality, result.multiplier, null, null);
                triggerPhase(player, recipe, guiItems, "success", resultItem, result.quality, result.multiplier, null, null);
                triggerQualityActions(player, recipe, guiItems, resultItem, qualityTier, result.quality, result.multiplier);
                return result;
            });
    }

    private ForgeResult buildActionFailure(Player player, Recipe recipe, GuiItems guiItems, ForgeResult result, ActionBatchResult batch) {
        ActionStepResult failure = batch.firstFailure();
        String reason = resolveFailureReason(failure);
        result.errorKey = DEFAULT_OPERATION_FAILURE_KEY;
        result.operationFailureReason = reason;
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("reason", reason);
        if (failure != null) {
            replacements.put("operation", failure.actionId());
            replacements.put("line", failure.lineNumber());
        }
        result.replacements = Map.copyOf(replacements);
        triggerPhase(
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

    private CompletableFuture<ActionBatchResult> executePhase(Player player,
                                                                 Recipe recipe,
                                                                 GuiItems guiItems,
                                                                 String phase,
                                                                 ItemStack resultItem,
                                                                 String quality,
                                                                 double multiplier,
                                                                 String errorKey,
                                                                 String failureReason) {
        return executeActionLines(
            player,
            recipe,
            guiItems,
            phase,
            phaseLines(recipe, phase),
            resultItem,
            quality,
            multiplier,
            errorKey,
            failureReason
        );
    }

    private CompletableFuture<ActionBatchResult> executeActionLines(Player player,
                                                                          Recipe recipe,
                                                                          GuiItems guiItems,
                                                                          String phase,
                                                                          List<String> lines,
                                                                          ItemStack resultItem,
                                                                          String quality,
                                                                          double multiplier,
                                                                          String errorKey,
                                                                          String failureReason) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (lines.isEmpty() || coreLib == null || coreLib.actionExecutor() == null) {
            return CompletableFuture.completedFuture(new ActionBatchResult(true, List.of()));
        }
        ActionContext context = buildActionContext(coreLib, player, recipe, guiItems, phase, resultItem, quality, multiplier, errorKey, failureReason);
        return coreLib.actionExecutor().executeAll(context, lines, true);
    }

    private void triggerPhase(Player player,
                              Recipe recipe,
                              GuiItems guiItems,
                              String phase,
                              ItemStack resultItem,
                              String quality,
                              double multiplier,
                              String errorKey,
                              String failureMessage) {
        executePhase(player, recipe, guiItems, phase, resultItem, quality, multiplier, errorKey, failureMessage)
            .whenComplete((batch, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().warning("Failed to execute forge action phase '" + phase + "' for recipe '" + recipe.id() + "': " + throwable.getMessage());
                    return;
                }
                if (batch != null && !batch.success()) {
                    plugin.getLogger().warning("Forge action phase '" + phase + "' failed for recipe '" + recipe.id() + "': " + resolveFailureReason(batch.firstFailure()));
                }
            });
    }

    private void triggerQualityActions(Player player,
                                       Recipe recipe,
                                       GuiItems guiItems,
                                       ItemStack resultItem,
                                       QualitySettings.QualityTier qualityTier,
                                       String quality,
                                       double multiplier) {
        if (qualityTier == null) {
            return;
        }
        List<String> lines = plugin.appConfig().qualitySettings().itemMetaActions(qualityTier.name());
        if (lines.isEmpty()) {
            return;
        }
        executeActionLines(player, recipe, guiItems, "quality", lines, resultItem, quality, multiplier, null, null)
            .whenComplete((batch, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().warning("Failed to execute forge quality actions for tier '" + qualityTier.name() + "': " + throwable.getMessage());
                    return;
                }
                if (batch != null && !batch.success()) {
                    plugin.getLogger().warning("Forge quality actions failed for tier '" + qualityTier.name() + "': " + resolveFailureReason(batch.firstFailure()));
                }
            });
    }

    private List<String> phaseLines(Recipe recipe, String phase) {
        if (recipe == null) {
            return List.of();
        }
        return switch (Texts.lower(phase)) {
            case "pre" -> recipe.action() == null ? List.of() : recipe.action().pre();
            case "result" -> recipe.result() == null ? List.of() : recipe.result().action();
            case "success" -> recipe.action() == null ? List.of() : recipe.action().success();
            case "failure" -> recipe.action() == null ? List.of() : recipe.action().failure();
            default -> List.of();
        };
    }

    private ActionContext buildActionContext(EmakiCoreLibPlugin coreLib,
                                                   Player player,
                                                   Recipe recipe,
                                                   GuiItems guiItems,
                                                   String phase,
                                                   ItemStack resultItem,
                                                   String quality,
                                                   double multiplier,
                                                   String errorKey,
                                                   String failureReason) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        String sourceItemName = resolveSourceItemName(guiItems, resultItem, recipe);
        String showItem = buildShowItemPlaceholder(guiItems, recipe, resultItem);
        placeholders.put("forge_recipe_id", recipe == null ? "" : recipe.id());
        placeholders.put("forge_recipe_name", recipe == null ? "" : recipe.displayName());
        placeholders.put("forge_source_item_name", sourceItemName);
        placeholders.put("forge_result_item_name", resolveResultItemName(recipe, resultItem));
        placeholders.put("forge_quality", Texts.toStringSafe(quality));
        placeholders.put("forge_multiplier", Numbers.formatNumber(multiplier, plugin.appConfig().defaultNumberFormat()));
        placeholders.put("forge_multiplier_raw", Double.toString(multiplier));
        placeholders.put("forge_error_key", Texts.toStringSafe(errorKey));
        placeholders.put("forge_failure_reason", Texts.toStringSafe(failureReason));
        placeholders.put("forge_show_item", showItem);
        placeholders.put("show_item", showItem);

        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfNotNull(attributes, "recipe", recipe);
        putIfNotNull(attributes, "guiItems", guiItems);
        putIfNotNull(attributes, "targetItem", guiItems == null ? null : guiItems.targetItem());
        putIfNotNull(attributes, "resultItem", resultItem);
        putIfNotNull(attributes, "quality", quality);
        attributes.put("multiplier", multiplier);
        putIfNotNull(attributes, "errorKey", errorKey);
        putIfNotNull(attributes, "failureReason", failureReason);

        boolean debug = plugin.appConfig().debug() || coreLib.configModel().actionDebug();
        return new ActionContext(plugin, player, phase, false, debug, placeholders, attributes);
    }

    private String resolveFailureReason(ActionStepResult failure) {
        if (failure == null || failure.result() == null) {
            return "Unknown forge action failure.";
        }
        if (Texts.isNotBlank(failure.result().errorMessage())) {
            return failure.result().errorMessage();
        }
        return "Action '" + failure.actionId() + "' failed.";
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (target != null && value != null) {
            target.put(key, value);
        }
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
            totalCapacity += material.capacityCost() * itemStack.getAmount();
            totalItems += itemStack.getAmount();
        }
        if (optionalConfig.maxCount() > 0 && totalItems > optionalConfig.maxCount()) {
            return ValidationResult.fail("forge.error.material_count_exceeded", Map.of("current", totalItems, "max", optionalConfig.maxCount()));
        }
        if (maxCapacity > 0 && totalCapacity > maxCapacity) {
            return ValidationResult.fail("forge.error.capacity_exceeded", Map.of("current", totalCapacity, "max", maxCapacity));
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

    private ItemStack createResultItem(Recipe recipe,
                                       GuiItems guiItems,
                                       double multiplier,
                                       QualitySettings.QualityTier qualityTier) {
        Recipe.ResultConfig resultConfig = recipe.result();
        if (resultConfig == null || resultConfig.outputItem() == null) {
            return null;
        }
        ItemStack base = plugin.itemIdentifierService().createItem(resultConfig.outputItem(), 1);
        if (base == null) {
            return null;
        }
        ItemStack resultItem = base.clone();
        applyResultMetaEffects(resultItem, resultConfig);
        List<MaterialContribution> materials = collectMaterialContributions(guiItems);
        applyMaterialEffects(resultItem, materials, multiplier);
        applyQualityMetaEffects(resultItem, qualityTier);
        plugin.pdcService().apply(resultItem, recipe, toPdcRecords(materials), qualityTier, multiplier);
        return resultItem;
    }

    private void applyResultMetaEffects(ItemStack itemStack, Recipe.ResultConfig resultConfig) {
        applyMetaActions(itemStack, resultConfig.nameModifications(), resultConfig.loreActions(), Map.of());
    }

    private void applyQualityMetaEffects(ItemStack itemStack, QualitySettings.QualityTier qualityTier) {
        if (qualityTier == null) {
            return;
        }
        QualitySettings settings = plugin.appConfig().qualitySettings();
        if (!settings.itemMetaEnabled()) {
            return;
        }
        applyMetaActions(
            itemStack,
            settings.itemMetaNameModifications(qualityTier.name()),
            settings.itemMetaLoreActions(qualityTier.name()),
            Map.of()
        );
    }

    private void applyMaterialEffects(ItemStack itemStack, List<MaterialContribution> materials, double multiplier) {
        Map<String, Double> contributions = new LinkedHashMap<>();
        List<Map<String, Object>> nameMods = new ArrayList<>();
        List<Map<String, Object>> loreOps = new ArrayList<>();
        for (MaterialContribution material : materials) {
            for (Map.Entry<String, Double> entry : material.material().statContributions().entrySet()) {
                contributions.merge(entry.getKey(), entry.getValue() * material.amount(), Double::sum);
            }
            nameMods.addAll(material.material().nameModifications());
            loreOps.addAll(material.material().loreActions());
        }
        Map<String, Double> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : contributions.entrySet()) {
            scaled.put(entry.getKey(), entry.getValue() * multiplier);
        }
        applyMetaActions(itemStack, nameMods, loreOps, scaled);
    }

    private List<MaterialContribution> collectMaterialContributions(GuiItems guiItems) {
        List<MaterialContribution> materials = new ArrayList<>();
        int sequence = 0;
        for (Map.Entry<Integer, ItemStack> entry : guiItems.requiredMaterials().entrySet()) {
            MaterialContribution contribution = toMaterialContribution(entry.getKey(), entry.getValue(), "required", sequence++);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : guiItems.optionalMaterials().entrySet()) {
            MaterialContribution contribution = toMaterialContribution(entry.getKey(), entry.getValue(), "optional", sequence++);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        materials.sort(Comparator.comparingInt((MaterialContribution value) -> value.material().priority())
            .thenComparingInt(MaterialContribution::sequence));
        return materials;
    }

    private MaterialContribution toMaterialContribution(int slot, ItemStack itemStack, String category, int sequence) {
        if (itemStack == null) {
            return null;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        ForgeMaterial material = findMaterialBySource(source);
        if (material == null) {
            return null;
        }
        return new MaterialContribution(material, itemStack.getAmount(), slot, category, sequence, source);
    }

    private List<ForgePdcService.MaterialRecord> toPdcRecords(List<MaterialContribution> materials) {
        List<ForgePdcService.MaterialRecord> records = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        for (MaterialContribution material : materials) {
            records.add(new ForgePdcService.MaterialRecord(
                material.category(),
                material.material().id(),
                material.amount(),
                material.slot(),
                material.sequence(),
                material.source(),
                timestamp
            ));
        }
        return records;
    }

    private void applyMetaActions(ItemStack itemStack,
                                     List<Map<String, Object>> nameModifications,
                                     List<Map<String, Object>> loreActions,
                                     Map<String, Double> statContributions) {
        if (itemStack == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        String currentName = itemMeta.hasDisplayName() ? MiniMessages.serialize(itemMeta.displayName()) : "";
        for (Map<String, Object> modification : nameModifications) {
            String operation = Texts.lower(modification.get("operation"));
            String value = Texts.toStringSafe(modification.get("value"));
            String regexPattern = Texts.toStringSafe(modification.get("regex_pattern"));
            String replacement = Texts.toStringSafe(modification.get("replacement"));
            switch (operation) {
                case "append_suffix" -> currentName = currentName + value;
                case "prepend_prefix" -> currentName = value + currentName;
                case "replace" -> currentName = value;
                case "regex_replace" -> {
                    try {
                        currentName = Pattern.compile(regexPattern).matcher(currentName).replaceAll(replacement);
                    } catch (Exception ignored) {
                    }
                }
                default -> {
                }
            }
        }
        if (Texts.isNotBlank(currentName)) {
            itemMeta.displayName(MiniMessages.parse(currentName));
        }
        List<Component> lore = new ArrayList<>(itemMeta.hasLore() && itemMeta.lore() != null ? itemMeta.lore() : List.of());
        for (Map<String, Object> operation : loreActions) {
            applyLoreAction(lore, operation, statContributions);
        }
        if (!lore.isEmpty()) {
            itemMeta.lore(lore);
        }
        itemStack.setItemMeta(itemMeta);
    }

    private void applyLoreAction(List<Component> lore,
                                    Map<String, Object> operation,
                                    Map<String, Double> statContributions) {
        String type = Texts.lower(operation.get("operation"));
        List<String> content = Texts.asStringList(operation.get("content"));
        String targetPattern = Texts.toStringSafe(operation.get("target_pattern"));
        String regexPattern = Texts.toStringSafe(operation.get("regex_pattern"));
        String replacement = Texts.toStringSafe(operation.get("replacement"));
        switch (type) {
            case "insert_below" -> insertRelative(lore, content, statContributions, targetPattern, true, false);
            case "insert_above" -> insertRelative(lore, content, statContributions, targetPattern, false, false);
            case "insert_below_regex" -> insertRelative(lore, content, statContributions, regexPattern, true, true);
            case "insert_above_regex" -> insertRelative(lore, content, statContributions, regexPattern, false, true);
            case "prepend_line", "prepend_lines", "append_first_line", "append_first_lines", "insert_first" ->
                insertAt(lore, 0, content, statContributions);
            case "append", "append_line", "append_lines" -> insertAt(lore, lore.size(), content, statContributions);
            case "replace_line" -> replaceLine(lore, targetPattern, content, statContributions);
            case "delete_line" -> deleteLine(lore, targetPattern, false);
            case "delete_line_regex" -> deleteLine(lore, regexPattern, true);
            case "regex_replace" -> replaceRegex(lore, regexPattern, replacement, statContributions);
            default -> {
            }
        }
    }

    private void insertRelative(List<Component> lore,
                                List<String> content,
                                Map<String, Double> statContributions,
                                String pattern,
                                boolean below,
                                boolean regex) {
        int index = -1;
        for (int line = 0; line < lore.size(); line++) {
            String plain = MiniMessages.plain(lore.get(line));
            boolean matches = regex ? Pattern.compile(pattern).matcher(plain).find() : plain.contains(pattern);
            if (matches) {
                index = below ? line + 1 : line;
                break;
            }
        }
        insertAt(lore, index < 0 ? lore.size() : index, content, statContributions);
    }

    private void insertAt(List<Component> lore, int index, List<String> content, Map<String, Double> statContributions) {
        int insertIndex = index;
        for (String line : content) {
            lore.add(insertIndex++, MiniMessages.parse(formatStatLine(line, statContributions)));
        }
    }

    private void replaceLine(List<Component> lore,
                             String targetPattern,
                             List<String> content,
                             Map<String, Double> statContributions) {
        for (int index = 0; index < lore.size(); index++) {
            String plain = MiniMessages.plain(lore.get(index));
            if (!plain.contains(targetPattern)) {
                continue;
            }
            String replacement = content.isEmpty() ? "" : formatStatLine(content.get(0), statContributions);
            lore.set(index, MiniMessages.parse(replacement));
            return;
        }
    }

    private void deleteLine(List<Component> lore, String pattern, boolean regex) {
        for (int index = lore.size() - 1; index >= 0; index--) {
            String plain = MiniMessages.plain(lore.get(index));
            boolean matches = regex ? Pattern.compile(pattern).matcher(plain).find() : plain.contains(pattern);
            if (matches) {
                lore.remove(index);
            }
        }
    }

    private void replaceRegex(List<Component> lore,
                              String regexPattern,
                              String replacement,
                              Map<String, Double> statContributions) {
        for (int index = 0; index < lore.size(); index++) {
            String plain = MiniMessages.plain(lore.get(index));
            try {
                String updated = Pattern.compile(regexPattern).matcher(plain).replaceAll(replacement);
                lore.set(index, MiniMessages.parse(formatStatLine(updated, statContributions)));
            } catch (Exception ignored) {
            }
        }
    }

    private String formatStatLine(String line, Map<String, Double> statContributions) {
        String formatted = Texts.toStringSafe(line);
        for (Map.Entry<String, Double> entry : statContributions.entrySet()) {
            formatted = formatted.replace(
                "{" + entry.getKey() + "}",
                Numbers.formatNumber(entry.getValue(), plugin.appConfig().defaultNumberFormat())
            );
        }
        return formatted;
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
        String resolvedItemName = resolveItemName(itemStack);
        if (Texts.isNotBlank(resolvedItemName)) {
            return resolvedItemName;
        }
        if (recipe != null && recipe.result() != null && recipe.result().outputItem() != null) {
            return recipe.result().outputItem().getIdentifier();
        }
        return "物品";
    }

    private String resolveSourceItemName(GuiItems guiItems, ItemStack resultItem, Recipe recipe) {
        String sourceName = resolveItemName(guiItems == null ? null : guiItems.targetItem());
        return Texts.isNotBlank(sourceName) ? sourceName : resolveResultItemName(recipe, resultItem);
    }

    private String resolveItemName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "";
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return MiniMessages.plain(itemStack.getItemMeta().displayName());
        }
        try {
            return MiniMessages.plain(itemStack.effectiveName());
        } catch (Exception ignored) {
            return itemStack.getType().name();
        }
    }

    private String buildShowItemPlaceholder(GuiItems guiItems, Recipe recipe, ItemStack resultItem) {
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
        Component display = resolveDisplayComponent(guiItems == null ? null : guiItems.targetItem());
        if (display == null) {
            display = resolveDisplayComponent(resultItem);
        }
        if (display == null) {
            display = MiniMessages.parse(resolveResultItemName(recipe, resultItem));
        }
        try {
            return MiniMessages.serialize(display.hoverEvent(resultItem.asHoverEvent(showItem -> showItem)));
        } catch (Exception ignored) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
    }

    private Component resolveDisplayComponent(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().displayName();
        }
        try {
            return itemStack.effectiveName();
        } catch (Exception ignored) {
            return Component.text(itemStack.getType().name());
        }
    }

    private String replacePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text) || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        return Texts.toStringSafe(PlaceholderAPI.setPlaceholders(player, text));
    }
}
