package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
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

    private record QualityRollPlan(QualitySettings.QualityTier tier,
                                   String qualityName,
                                   double multiplier) {
    }

    public record PreparedForge(EmakiItemAssemblyRequest request,
                                QualitySettings.QualityTier qualityTier,
                                String quality,
                                double multiplier,
                                ItemStack previewItem) {
        public PreparedForge {
            previewItem = previewItem == null ? null : previewItem.clone();
        }
    }

    private record GuaranteePolicy(boolean enabled, int threshold, String minimumName) {
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

    public String buildPreviewFingerprint(Player player, Recipe recipe, GuiItems guiItems) {
        List<Object> parts = new ArrayList<>();
        parts.add(player == null ? "" : player.getUniqueId().toString());
        parts.add(recipe == null ? "" : recipe.id());
        parts.add(player == null || recipe == null ? 0 : plugin.playerDataStore().guaranteeCounter(player.getUniqueId(), recipe.id()));
        appendItemSignature(parts, "target", guiItems == null ? null : guiItems.targetItem());
        appendMappedSignatures(parts, "blueprint", guiItems == null ? null : guiItems.blueprints());
        appendMappedSignatures(parts, "required", guiItems == null ? null : guiItems.requiredMaterials());
        appendMappedSignatures(parts, "optional", guiItems == null ? null : guiItems.optionalMaterials());
        return SignatureUtil.stableSignature(parts);
    }

    public ItemStack previewResultItem(Player player,
                                       Recipe recipe,
                                       GuiItems guiItems,
                                       long previewSeed,
                                       long forgedAt) {
        PreparedForge preparedForge = prepareForge(player, recipe, guiItems, previewSeed, forgedAt);
        return preparedForge == null || preparedForge.previewItem() == null ? null : preparedForge.previewItem().clone();
    }

    public PreparedForge prepareForge(Player player,
                                      Recipe recipe,
                                      GuiItems guiItems,
                                      long previewSeed,
                                      long forgedAt) {
        if (recipe == null) {
            return null;
        }
        QualityRollPlan rollPlan = resolveQualityRoll(
            player == null ? null : player.getUniqueId(),
            recipe,
            buildRollKey(buildPreviewFingerprint(player, recipe, guiItems), previewSeed)
        );
        EmakiItemAssemblyRequest request = resultItemFactory.buildAssemblyRequest(recipe, guiItems, rollPlan.multiplier(), rollPlan.tier(), forgedAt);
        if (request == null) {
            return null;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        ItemStack previewItem = coreLib == null ? null : coreLib.itemAssemblyService().preview(request);
        return new PreparedForge(request, rollPlan.tier(), rollPlan.qualityName(), rollPlan.multiplier(), previewItem);
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge) {
        ForgeResult result = new ForgeResult();
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
                PreparedForge forgePlan = preparedForge;
                if (forgePlan == null) {
                    forgePlan = prepareForge(player, recipe, guiItems, 0L, System.currentTimeMillis());
                }
                if (forgePlan == null || forgePlan.request() == null) {
                    result.errorKey = "forge.error.item_create";
                    result.replacements = Map.of();
                    actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, null, 1D, result.errorKey, "Unable to prepare forge assembly request.");
                    return result;
                }
                result.quality = forgePlan.quality();
                result.multiplier = forgePlan.multiplier();
                EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
                ItemStack resultItem = coreLib == null ? null : coreLib.itemAssemblyService().give(player, forgePlan.request());
                if (resultItem == null) {
                    result.errorKey = "forge.error.item_create";
                    result.replacements = Map.of();
                    actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, result.quality, result.multiplier, result.errorKey, "Unable to create forge result item.");
                    return result;
                }
                applyGuaranteeOutcome(player.getUniqueId(), recipe, forgePlan.qualityTier());
                plugin.playerDataStore().recordCraft(player.getUniqueId(), recipe.id());
                result.success = true;
                result.resultItem = resultItem;
                actionCoordinator.triggerPhase(player, recipe, guiItems, "result", resultItem, result.quality, result.multiplier, null, null);
                actionCoordinator.triggerPhase(player, recipe, guiItems, "success", resultItem, result.quality, result.multiplier, null, null);
                actionCoordinator.triggerQualityActions(player, recipe, guiItems, resultItem, forgePlan.qualityTier(), result.quality, result.multiplier);
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

    private QualityRollPlan resolveQualityRoll(UUID playerId, Recipe recipe, String rollKey) {
        QualitySettings settings = plugin.appConfig().qualitySettings();
        Recipe.QualityConfig qualityConfig = recipe == null ? Recipe.QualityConfig.defaults() : recipe.quality();
        List<QualitySettings.QualityTier> tiers = resolveQualityPool(qualityConfig, settings);
        GuaranteePolicy guaranteePolicy = resolveGuaranteePolicy(qualityConfig, settings);
        if (guaranteePolicy.enabled() && playerId != null && recipe != null) {
            int counter = plugin.playerDataStore().guaranteeCounter(playerId, recipe.id());
            if (counter >= guaranteePolicy.threshold() - 1) {
                QualitySettings.QualityTier guaranteed = findMinimumTier(qualityConfig.customPool(), guaranteePolicy.minimumName());
                if (guaranteed == null) {
                    guaranteed = settings.minimumTier();
                }
                return new QualityRollPlan(guaranteed, guaranteed.name(), guaranteed.multiplier());
            }
        }
        QualitySettings.QualityTier rolled = deterministicWeightedTier(tiers, rollKey);
        if (rolled == null) {
            rolled = settings.defaultTier();
        }
        return new QualityRollPlan(rolled, rolled.name(), rolled.multiplier());
    }

    private void applyGuaranteeOutcome(UUID playerId, Recipe recipe, QualitySettings.QualityTier rolledTier) {
        if (playerId == null || recipe == null || rolledTier == null) {
            return;
        }
        QualitySettings settings = plugin.appConfig().qualitySettings();
        GuaranteePolicy guaranteePolicy = resolveGuaranteePolicy(recipe.quality(), settings);
        if (!guaranteePolicy.enabled()) {
            return;
        }
        int counter = plugin.playerDataStore().guaranteeCounter(playerId, recipe.id());
        if (counter >= guaranteePolicy.threshold() - 1) {
            plugin.playerDataStore().resetGuaranteeCounter(playerId, recipe.id());
            return;
        }
        List<QualitySettings.QualityTier> tiers = resolveQualityPool(recipe.quality(), settings);
        QualitySettings.QualityTier minimumTier = findMinimumTier(recipe.quality().customPool(), guaranteePolicy.minimumName());
        if (minimumTier == null) {
            minimumTier = settings.minimumTier();
        }
        if (tierIndex(tiers, rolledTier) < tierIndex(tiers, minimumTier)) {
            plugin.playerDataStore().incrementGuaranteeCounter(playerId, recipe.id());
        } else {
            plugin.playerDataStore().resetGuaranteeCounter(playerId, recipe.id());
        }
    }

    private List<QualitySettings.QualityTier> resolveQualityPool(Recipe.QualityConfig qualityConfig, QualitySettings settings) {
        List<QualitySettings.QualityTier> tiers = new ArrayList<>();
        if (qualityConfig != null && qualityConfig.enabled() && !qualityConfig.customPool().isEmpty()) {
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
        return tiers;
    }

    private GuaranteePolicy resolveGuaranteePolicy(Recipe.QualityConfig qualityConfig, QualitySettings settings) {
        boolean enabled = qualityConfig != null && qualityConfig.enabled() && qualityConfig.guaranteeEnabled();
        int threshold = qualityConfig == null ? 0 : qualityConfig.guaranteeAttempts();
        String minimum = qualityConfig == null ? "" : qualityConfig.guaranteeMinimum();
        if (!enabled && settings.guaranteeEnabled()) {
            enabled = true;
            threshold = settings.guaranteeThreshold();
            minimum = settings.minimumTier().name();
        }
        return new GuaranteePolicy(enabled, Math.max(1, threshold), minimum);
    }

    private QualitySettings.QualityTier deterministicWeightedTier(List<QualitySettings.QualityTier> tiers, String rollKey) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        double totalWeight = 0D;
        for (QualitySettings.QualityTier tier : tiers) {
            if (tier != null && tier.weight() > 0D) {
                totalWeight += tier.weight();
            }
        }
        if (totalWeight <= 0D) {
            return null;
        }
        double ratio = deterministicRatio(rollKey);
        double roll = ratio * totalWeight;
        double cumulative = 0D;
        for (QualitySettings.QualityTier tier : tiers) {
            if (tier == null || tier.weight() <= 0D) {
                continue;
            }
            cumulative += tier.weight();
            if (roll <= cumulative) {
                return tier;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    private double deterministicRatio(String rollKey) {
        String signature = SignatureUtil.sha256(Texts.isBlank(rollKey) ? "forge" : rollKey);
        String sample = signature.substring(0, Math.min(12, signature.length()));
        try {
            long value = Long.parseLong(sample, 16);
            long max = (1L << (sample.length() * 4)) - 1L;
            return max <= 0L ? 0D : value / (double) max;
        } catch (Exception ignored) {
            return 0.5D;
        }
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

    private String buildRollKey(String fingerprint, long previewSeed) {
        return SignatureUtil.combine(fingerprint, Long.toUnsignedString(previewSeed));
    }

    private void appendMappedSignatures(List<Object> parts, String prefix, Map<Integer, ItemStack> items) {
        if (parts == null || items == null || items.isEmpty()) {
            return;
        }
        List<Integer> slots = new ArrayList<>(items.keySet());
        slots.sort(Integer::compareTo);
        for (Integer slot : slots) {
            appendItemSignature(parts, prefix + ":" + slot, items.get(slot));
        }
    }

    private void appendItemSignature(List<Object> parts, String prefix, ItemStack itemStack) {
        if (parts == null || itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        parts.add(prefix);
        parts.add(source == null ? "" : ItemSourceUtil.toShorthand(source));
        parts.add(itemStack.getAmount());
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
