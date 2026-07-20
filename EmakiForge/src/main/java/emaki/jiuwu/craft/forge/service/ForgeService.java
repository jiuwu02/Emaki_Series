package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;
import emaki.jiuwu.craft.forge.model.ValidationResult;

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
    private final ForgeLookupIndex lookupIndex;
    private final RecipeMatchingService recipeMatchingService;
    private final ForgeExecutionService forgeExecutionService;
    private final ForgeLayerSnapshotBuilder layerSnapshotBuilder;
    private final PreparedForgeCache preparedForgeCache;
    private final ForgeValidationService validationService;
    private final ForgeFingerprintService fingerprintService;
    private final ForgePreparationService preparationService;
    private final ForgePerformanceRecorder performanceRecorder;
    private final ForgeAsyncExecutor asyncExecutor;
    private final ForgeResultPostProcessor resultPostProcessor;

    public ForgeService(EmakiForgePlugin plugin,
            AsyncTaskScheduler asyncTaskScheduler,
            PerformanceMonitor performanceMonitor,
            EmakiItemAssemblyService itemAssemblyService,
            Supplier<ActionExecutor> actionExecutorSupplier,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.layerSnapshotBuilder = new ForgeLayerSnapshotBuilder(plugin);
        this.resultItemFactory = new ForgeResultItemFactory(plugin);
        ForgePdcAttributeWriter pdcAttributeWriter = new ForgePdcAttributeWriter(plugin);
        ForgeActionCoordinator actionCoordinator = new ForgeActionCoordinator(plugin, resultItemFactory, actionExecutorSupplier);
        this.lookupIndex = new ForgeLookupIndex(plugin);
        MaterialValidationService materialValidationService = new MaterialValidationService(plugin, lookupIndex);
        QualityCalculationService qualityCalculationService = new QualityCalculationService(
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

            @Override
            public int counter(UUID playerId, long generation, String key) {
                return plugin.playerDataStore().guaranteeCounterIfCurrent(playerId, generation, key);
            }

            @Override
            public void increment(UUID playerId, long generation, String key) {
                plugin.playerDataStore().incrementGuaranteeCounterIfCurrent(playerId, generation, key);
            }

            @Override
            public void reset(UUID playerId, long generation, String key) {
                plugin.playerDataStore().resetGuaranteeCounterIfCurrent(playerId, generation, key);
            }
        },
                this::resolveMaterialQualityModifiers
        );
        this.preparedForgeCache = new PreparedForgeCache();
        this.validationService = new ForgeValidationService(plugin, materialValidationService);
        this.fingerprintService = new ForgeFingerprintService(plugin);
        this.preparationService = new ForgePreparationService(
                plugin,
                qualityCalculationService,
                resultItemFactory,
                fingerprintService,
                preparedForgeCache
        );
        this.performanceRecorder = new ForgePerformanceRecorder(performanceMonitor);
        this.asyncExecutor = new ForgeAsyncExecutor(asyncTaskScheduler);
        this.resultPostProcessor = new ForgeResultPostProcessor(
                plugin,
                layerSnapshotBuilder,
                pdcAttributeWriter,
                new ItemOperationLedger(plugin::debugLogger)
        );
        this.recipeMatchingService = new RecipeMatchingService(
                this::candidateRecipes,
                lookupIndex::sortedRecipes,
                (player, recipe) -> guiItems -> canForge(player, recipe, guiItems)
        );
        this.forgeExecutionService = new ForgeExecutionService(
                plugin,
                executionDispatcher,
                threadOwnership,
                (playerId, generation) -> plugin.playerDataStore().isCurrentGeneration(playerId, generation),
                actionCoordinator,
                qualityCalculationService,
                (player, recipe, guiItems, preparedForge) -> preparedForge == null
                        ? prepareForge(player, recipe, guiItems, 0L, System.currentTimeMillis())
                        : preparedForge,
                new ForgeExecutionService.ResultItemGiver() {
                    @Override
                    public ItemStack give(Player player, EmakiItemAssemblyRequest request) {
                        return itemAssemblyService == null ? null : itemAssemblyService.give(player, request);
                    }

                    @Override
                    public ItemStack preview(EmakiItemAssemblyRequest request) {
                        return itemAssemblyService == null ? null : itemAssemblyService.preview(request);
                    }
                },
                (playerId, generation, recipeId, guaranteeUpdate) -> plugin.playerDataStore()
                        .recordSuccessfulForgeIfCurrent(playerId, generation, recipeId, guaranteeUpdate),
                resultPostProcessor::process,
                plugin.javaScriptForgeRuleRegistry(),
                plugin.javaScriptResultHookRegistry()
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
        return lookupIndex.findMaterialById(materialId);
    }

    public BlueprintRequirement findBlueprintRequirementBySource(ItemSource source) {
        return lookupIndex.findBlueprintRequirementBySource(source);
    }

    public RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems) {
        return recipeMatchingService.findMatchingRecipe(player, guiItems);
    }

    private List<Recipe> candidateRecipes(GuiItems guiItems) {
        List<Recipe> candidates = new ArrayList<>(lookupIndex.genericRecipes());
        ItemSource inputSource = guiItems == null || guiItems.targetItem() == null
                ? null
                : plugin.itemIdentifierService().identifyItem(guiItems.targetItem());
        if (inputSource == null) {
            if (candidates.isEmpty()) {
                return lookupIndex.sortedRecipes();
            }
            return List.copyOf(candidates);
        }
        candidates.addAll(lookupIndex.findRecipesByConfiguredOutputSource(inputSource));
        return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
    }

    public ValidationResult canForge(Player player, Recipe recipe, GuiItems guiItems) {
        return validationService.canForge(player, recipe, guiItems);
    }

    public String buildPreviewFingerprint(Player player, Recipe recipe, GuiItems guiItems) {
        return fingerprintService.buildPreviewFingerprint(player, recipe, guiItems);
    }

    public ItemStack previewResultItem(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        return null;
    }

    public PreparedForge prepareForge(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        return performanceRecorder.measure("forge-prepare", () -> preparationService.prepareForge(player, recipe, guiItems, previewSeed, forgedAt));
    }

    public CompletableFuture<PreparedForge> prepareForgeAsync(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        return asyncExecutor.supplyPrepare(() -> prepareForge(player, recipe, guiItems, previewSeed, forgedAt));
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
            Recipe recipe,
            GuiItems guiItems,
            PreparedForge preparedForge) {
        ValidationResult validation = canForge(player, recipe, guiItems);
        long sessionGeneration = player == null
                ? 0L
                : plugin.playerDataStore().ensureCurrentGeneration(player.getUniqueId());
        if (player != null && !plugin.playerDataStore().isSessionWritable(player.getUniqueId())) {
            ForgeResult result = new ForgeResult();
            result.setErrorKey("forge.error.action_failed");
            result.setReplacements(Map.of("reason", "player data is not ready"));
            return CompletableFuture.completedFuture(result);
        }
        return forgeExecutionService.execute(
                player,
                recipe,
                guiItems,
                preparedForge,
                validation,
                sessionGeneration
        );
    }

    public String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        return resultItemFactory.resolveResultItemName(recipe, itemStack);
    }

    private List<ForgeMaterial.QualityModifier> resolveMaterialQualityModifiers(Recipe recipe, GuiItems guiItems) {
        if (recipe == null || guiItems == null) {
            return List.of();
        }
        return layerSnapshotBuilder.collectQualityModifiers(layerSnapshotBuilder.collectMaterialContributions(recipe, guiItems));
    }
}
