package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.cache.CacheManager;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;
import emaki.jiuwu.craft.forge.model.ValidationResult;
import me.clip.placeholderapi.PlaceholderAPI;

public final class ForgeService {

    public record PreparedForge(EmakiItemAssemblyRequest request,
            QualitySettings.QualityTier rolledQualityTier,
            boolean forceQualityApplied,
            emaki.jiuwu.craft.forge.model.QualitySettings.QualityTier qualityTier,
            String quality,
            double multiplier,
            ItemStack previewItem) {

        public PreparedForge       {
            previewItem = previewItem == null ? null : previewItem.clone();
        }
    }

    private final EmakiForgePlugin plugin;
    private final ForgeResultItemFactory resultItemFactory;
    private final ForgeActionCoordinator actionCoordinator;
    private final ForgeLookupIndex lookupIndex;
    private final QualityCalculationService qualityCalculationService;
    private final MaterialValidationService materialValidationService;
    private final RecipeMatchingService recipeMatchingService;
    private final ForgeExecutionService forgeExecutionService;
    private final CacheManager<String, PreparedForge> preparedForgeCache = new CacheManager<>(128, 30_000L);
    private final AsyncTaskScheduler asyncTaskScheduler;
    private final PerformanceMonitor performanceMonitor;

    public ForgeService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        EmakiCoreLibPlugin coreLibPlugin = EmakiCoreLibPlugin.getInstance();
        this.asyncTaskScheduler = coreLibPlugin == null ? null : coreLibPlugin.asyncTaskScheduler();
        this.performanceMonitor = coreLibPlugin == null ? null : coreLibPlugin.performanceMonitor();
        this.resultItemFactory = new ForgeResultItemFactory(plugin);
        this.actionCoordinator = new ForgeActionCoordinator(plugin, resultItemFactory);
        this.lookupIndex = new ForgeLookupIndex(plugin);
        this.materialValidationService = new MaterialValidationService(plugin, lookupIndex);
        this.qualityCalculationService = new QualityCalculationService(
                () -> plugin.appConfig().qualitySettings(),
                new QualityCalculationService.GuaranteeCounterStore() {
            @Override
            public int counter(UUID playerId, String key) {
                return plugin.playerDataStore().guaranteeCounter(playerId, key);
            }

            @Override
            public void increment(UUID playerId, String key) {
                plugin.playerDataStore().incrementGuaranteeCounter(playerId, key);
            }

            @Override
            public void reset(UUID playerId, String key) {
                plugin.playerDataStore().resetGuaranteeCounter(playerId, key);
            }
        },
                this::resolveMaterialQualityModifiers
        );
        this.recipeMatchingService = new RecipeMatchingService(
                lookupIndex::sortedRecipes,
                (player, recipe) -> guiItems -> canForge(player, recipe, guiItems)
        );
        this.forgeExecutionService = new ForgeExecutionService(
                actionCoordinator,
                qualityCalculationService,
                (player, recipe, guiItems, preparedForge) -> preparedForge == null
                        ? prepareForge(player, recipe, guiItems, 0L, System.currentTimeMillis())
                        : preparedForge,
                (player, request) -> {
                    EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
                    return coreLib == null ? null : coreLib.itemAssemblyService().give(player, request);
                },
                (playerId, recipeId) -> plugin.playerDataStore().recordCraft(playerId, recipeId)
        );
    }

    public void refreshIndexes() {
        lookupIndex.refresh();
        preparedForgeCache.clear();
    }

    public List<Recipe> sortedRecipes() {
        return lookupIndex.sortedRecipes();
    }

    public ForgeMaterial findMaterialBySource(ItemSource source) {
        return lookupIndex.findMaterialBySource(source);
    }

    public ForgeMaterial findMaterialById(String materialId) {
        if (Texts.isBlank(materialId)) {
            return null;
        }
        for (Recipe recipe : lookupIndex.sortedRecipes()) {
            ForgeMaterial material = recipe.findMaterialByItem(materialId);
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    public BlueprintRequirement findBlueprintRequirementBySource(ItemSource source) {
        return lookupIndex.findBlueprintRequirementBySource(source);
    }

    public RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems) {
        return recipeMatchingService.findMatchingRecipe(player, guiItems);
    }

    public ValidationResult canForge(Player player, Recipe recipe, GuiItems guiItems) {
        if (recipe == null) {
            return ValidationResult.fail("forge.error.no_recipe");
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
        if (recipe.requiresTargetInput()) {
            if (guiItems.targetItem() == null) {
                return ValidationResult.fail("forge.error.no_target_item");
            }
        }
        return materialValidationService.validate(recipe, guiItems);
    }

    public String buildPreviewFingerprint(Player player, Recipe recipe, GuiItems guiItems) {
        List<Object> parts = new ArrayList<>();
        parts.add(player == null ? "" : player.getUniqueId().toString());
        parts.add(recipe == null ? "" : recipe.id());
        parts.add(player == null || recipe == null ? 0 : plugin.playerDataStore().guaranteeCounter(player.getUniqueId(), recipe.id()));
        if (recipe != null && recipe.requiresTargetInput()) {
            appendItemSignature(parts, "target", guiItems == null ? null : guiItems.targetItem());
        }
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
        // Forge GUI intentionally does not expose preview items to avoid rerolling random outcomes.
        return null;
    }

    public PreparedForge prepareForge(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        return measure("forge-prepare", () -> prepareForgeInternal(player, recipe, guiItems, previewSeed, forgedAt));
    }

    public CompletableFuture<PreparedForge> prepareForgeAsync(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        if (asyncTaskScheduler == null) {
            return CompletableFuture.completedFuture(prepareForge(player, recipe, guiItems, previewSeed, forgedAt));
        }
        return asyncTaskScheduler.supplyAsync(
                "forge-prepare",
                AsyncTaskScheduler.TaskPriority.NORMAL,
                10_000L,
                () -> prepareForge(player, recipe, guiItems, previewSeed, forgedAt)
        );
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
            Recipe recipe,
            GuiItems guiItems,
            PreparedForge preparedForge) {
        ValidationResult validation = canForge(player, recipe, guiItems);
        return forgeExecutionService.execute(player, recipe, guiItems, preparedForge, validation);
    }

    private String buildRollKey(String fingerprint, long previewSeed) {
        return SignatureUtil.combine(fingerprint, Long.toUnsignedString(previewSeed));
    }

    private String buildPreparationCacheKey(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        return SignatureUtil.stableSignature(List.of(
                buildPreviewFingerprint(player, recipe, guiItems),
                Long.toUnsignedString(previewSeed),
                Long.toUnsignedString(forgedAt)
        ));
    }

    private PreparedForge copyPreparedForge(PreparedForge source) {
        if (source == null) {
            return null;
        }
        EmakiItemAssemblyRequest request = copyAssemblyRequest(source.request());
        return new PreparedForge(
                request,
                source.rolledQualityTier(),
                source.forceQualityApplied(),
                source.qualityTier(),
                source.quality(),
                source.multiplier(),
                source.previewItem()
        );
    }

    private EmakiItemAssemblyRequest copyAssemblyRequest(EmakiItemAssemblyRequest request) {
        if (request == null) {
            return null;
        }
        ItemStack existingItem = request.existingItem() == null ? null : request.existingItem().clone();
        return new EmakiItemAssemblyRequest(
                request.baseSource(),
                request.amount(),
                existingItem,
                request.layerSnapshots(),
                request.feedbackPlayerId()
        );
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

    public String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        return resultItemFactory.resolveResultItemName(recipe, itemStack);
    }

    private List<ForgeMaterial.QualityModifier> resolveMaterialQualityModifiers(Recipe recipe, GuiItems guiItems) {
        List<ForgeMaterial.QualityModifier> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        appendMaterialQualityModifiers(result, recipe, guiItems.requiredMaterials().values(), false);
        appendMaterialQualityModifiers(result, recipe, guiItems.optionalMaterials().values(), true);
        return result;
    }

    private void appendMaterialQualityModifiers(List<ForgeMaterial.QualityModifier> result,
            Recipe recipe,
            Collection<ItemStack> items,
            boolean optional) {
        if (result == null || items == null) {
            return;
        }
        for (ItemStack itemStack : items) {
            ForgeMaterial material = itemStack == null || recipe == null
                    ? null
                    : recipe.findMaterialBySource(plugin.itemIdentifierService().identifyItem(itemStack), optional);
            if (material == null || itemStack.getAmount() <= 0) {
                continue;
            }
            result.addAll(material.qualityModifiers());
        }
    }

    private String replacePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text) || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        return Texts.toStringSafe(PlaceholderAPI.setPlaceholders(player, text));
    }

    private PreparedForge prepareForgeInternal(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        if (recipe == null) {
            return null;
        }
        String cacheKey = buildPreparationCacheKey(player, recipe, guiItems, previewSeed, forgedAt);
        PreparedForge cached = preparedForgeCache.get(cacheKey);
        if (cached != null) {
            return copyPreparedForge(cached);
        }
        QualityCalculationService.QualityRollPlan rollPlan = qualityCalculationService.resolveQualityRoll(
                player == null ? null : player.getUniqueId(),
                recipe,
                guiItems,
                buildRollKey(buildPreviewFingerprint(player, recipe, guiItems), previewSeed)
        );
        EmakiItemAssemblyRequest request = resultItemFactory.buildAssemblyRequest(recipe, guiItems, rollPlan.multiplier(), rollPlan.finalTier(), forgedAt);
        if (request == null) {
            return null;
        }
        PreparedForge preparedForge = new PreparedForge(
                request,
                rollPlan.rolledTier(),
                rollPlan.forceApplied(),
                rollPlan.finalTier(),
                rollPlan.qualityName(),
                rollPlan.multiplier(),
                null
        );
        preparedForgeCache.put(cacheKey, preparedForge);
        return copyPreparedForge(preparedForge);
    }

    private <T> T measure(String metricKey, SupplierWithException<T> supplier) {
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            T value = supplier.get();
            success = true;
            return value;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            if (performanceMonitor != null) {
                performanceMonitor.record(metricKey, System.nanoTime() - startedAt, success);
            }
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {

        T get() throws Exception;
    }
}
