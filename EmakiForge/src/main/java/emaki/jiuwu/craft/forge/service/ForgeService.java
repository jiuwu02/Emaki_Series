package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.ForgeRuntimeSnapshot;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
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
                                QualitySettings.QualityTier qualityTier,
                                String quality,
                                double multiplier,
                                ItemStack previewItem) {

        public PreparedForge {
            previewItem = previewItem == null ? null : previewItem.clone();
        }
    }

    private final EmakiForgePlugin plugin;
    private final ForgeResultItemFactory resultItemFactory;
    private final EmakiItemAssemblyService itemAssemblyService;
    private final ForgeMasteryService masteryService;
    private final ForgeLookupIndex lookupIndex;
    private final RecipeMatchingService recipeMatchingService;
    private final ForgeExecutionService forgeExecutionService;
    private final ForgeLayerSnapshotBuilder layerSnapshotBuilder;
    private final PreparedForgeCache preparedForgeCache;
    private final ForgeValidationService validationService;
    private final ForgeFingerprintService fingerprintService;
    private final ForgePreparationService preparationService;
    private final ForgePerformanceRecorder performanceRecorder;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final ForgeResultPostProcessor resultPostProcessor;
    private final AtomicBoolean accepting = new AtomicBoolean(false);
    private final CraftOperationJournal<Void> operationJournal = CraftOperationJournal.ofMemory(Integer.MAX_VALUE);

    public ForgeService(EmakiForgePlugin plugin,
                        AsyncTaskScheduler asyncTaskScheduler,
                        PerformanceMonitor performanceMonitor,
                        EmakiItemAssemblyService itemAssemblyService,
                        ActionLineRunner actionLines,
                        ExecutionDispatcher executionDispatcher,
                        ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.itemAssemblyService = itemAssemblyService;
        this.masteryService = new ForgeMasteryService(plugin == null ? null : plugin.playerDataStore());
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
        this.layerSnapshotBuilder = new ForgeLayerSnapshotBuilder(plugin);
        this.resultItemFactory = new ForgeResultItemFactory(plugin);
        ForgePdcAttributeWriter pdcAttributeWriter = new ForgePdcAttributeWriter(plugin);
        ForgeActionCoordinator actionCoordinator = new ForgeActionCoordinator(plugin, resultItemFactory, actionLines);
        this.lookupIndex = new ForgeLookupIndex();
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
                resultPostProcessor::process
        );
    }

    public ForgeLookupIndex.Snapshot buildLookupSnapshot(long generation,
                                                         Map<String, Recipe> recipes,
                                                         RecipeLoader.RecipeLoadReport report) {
        int invalidCount = report == null ? 0 : report.skipped();
        int issueCount = report == null ? 0 : report.issueCount();
        return lookupIndex.build(generation, recipes, invalidCount, issueCount);
    }

    public void installLookupSnapshot(ForgeLookupIndex.Snapshot snapshot) {
        lookupIndex.install(snapshot);
        preparedForgeCache.clear();
    }

    public ForgeLookupIndex.Snapshot lookupSnapshot() {
        return lookupIndex.snapshot();
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    public int inFlight() {
        return operationJournal.inFlightCount();
    }

    public void resumeAccepting() {
        ForgeRuntimeSnapshot runtime = plugin.runtimeSnapshot();
        accepting.set(runtime != null && runtime.available());
    }

    public CompletableFuture<Void> quiesce() {
        accepting.set(false);
        return operationJournal.quiesce();
    }

    public void close() {
        accepting.set(false);
        preparedForgeCache.clear();
    }

    public List<Recipe> sortedRecipes() {
        return lookupIndex.sortedRecipes();
    }

    public ForgeMaterial findMaterialBySource(ItemSourceRef source) {
        return lookupIndex.findMaterialBySource(source);
    }

    public ForgeMaterial findMaterialById(String materialId) {
        return lookupIndex.findMaterialById(materialId);
    }

    public BlueprintRequirement findBlueprintRequirementBySource(ItemSourceRef source) {
        return lookupIndex.findBlueprintRequirementBySource(source);
    }

    public RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems) {
        return recipeMatchingService.findMatchingRecipe(player, guiItems);
    }

    private List<Recipe> candidateRecipes(GuiItems guiItems) {
        List<Recipe> candidates = new ArrayList<>(lookupIndex.genericRecipes());
        ItemSourceRef inputSource = guiItems == null || guiItems.targetItem() == null
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
        if (!accepting.get() || !plugin.isRuntimeReady()) {
            return ValidationResult.fail("forge.error.runtime_unavailable", Map.of(
                    "state", plugin.runtimeStatus().name().toLowerCase(Locale.ROOT)));
        }
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
        PreparedForge preparedForge = prepareForge(player, recipe, guiItems, previewSeed, forgedAt);
        if (preparedForge == null || preparedForge.request() == null || itemAssemblyService == null) {
            return null;
        }
        ItemStack preview = itemAssemblyService.preview(preparedForge.request());
        if (preview == null) {
            return null;
        }
        resultPostProcessor.process(player, recipe, guiItems, preparedForge, preview);
        return preview;
    }

    public int mastery(UUID playerId, String recipeId) {
        return masteryService.getMastery(playerId, recipeId);
    }

    public PreparedForge prepareForge(Player player,
                                      Recipe recipe,
                                      GuiItems guiItems,
                                      long previewSeed,
                                      long forgedAt) {
        if (!accepting.get() || !plugin.isRuntimeReady() || !isPreparationThreadOwned(player)) {
            return null;
        }
        return performanceRecorder.measure("forge-prepare", () -> preparationService.prepareForge(player, recipe, guiItems, previewSeed, forgedAt));
    }

    public CompletableFuture<PreparedForge> prepareForgeAsync(Player player,
                                                              Recipe recipe,
                                                              GuiItems guiItems,
                                                              long previewSeed,
                                                              long forgedAt) {
        ForgeRuntimeSnapshot runtime = plugin.runtimeSnapshot();
        long runtimeGeneration = runtime == null ? 0L : runtime.generation();
        RequestPermit permit = acquire(runtimeGeneration);
        if (permit == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<PreparedForge> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                if (!plugin.isGenerationActive(runtimeGeneration)) {
                    future.complete(null);
                    return;
                }
                future.complete(prepareForge(player, recipe, guiItems, previewSeed, forgedAt));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        try {
            if (isPreparationThreadOwned(player)) {
                task.run();
            } else if (executionDispatcher == null) {
                future.completeExceptionally(new RejectedExecutionException(
                        "Forge preparation requires an execution dispatcher."));
            } else {
                var scheduled = player == null
                        ? executionDispatcher.runGlobal(plugin, task)
                        : executionDispatcher.runEntity(plugin, player, task,
                                () -> future.completeExceptionally(new RejectedExecutionException(
                                        "Forge player preparation retired before execution.")));
                if (scheduled == null) {
                    future.completeExceptionally(new RejectedExecutionException(
                            "Forge preparation scheduling was rejected."));
                }
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future.whenComplete((ignored, throwable) -> permit.close());
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge) {
        return executeForgeAsync(player, recipe, guiItems, preparedForge, null, null, null);
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge,
                                                            Runnable deliveryCommit) {
        return executeForgeAsync(player, recipe, guiItems, preparedForge, null, null, deliveryCommit);
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge,
                                                            BooleanSupplier deliveryClaim,
                                                            Runnable deliveryRollback,
                                                            Runnable deliveryCommit) {
        ForgeRuntimeSnapshot runtime = plugin.runtimeSnapshot();
        long runtimeGeneration = runtime == null ? 0L : runtime.generation();
        return executeForgeAsync(player, recipe, guiItems, preparedForge, runtimeGeneration,
                deliveryClaim, deliveryRollback, deliveryCommit);
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge,
                                                            double successRate) {
        ForgeRuntimeSnapshot runtime = plugin.runtimeSnapshot();
        long runtimeGeneration = runtime == null ? 0L : runtime.generation();
        return executeForgeAsync(player, recipe, guiItems, preparedForge, runtimeGeneration,
                successRate, null, null, null);
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge,
                                                            long runtimeGeneration,
                                                            BooleanSupplier deliveryClaim,
                                                            Runnable deliveryRollback,
                                                            Runnable deliveryCommit) {
        return executeForgeAsync(player, recipe, guiItems, preparedForge, runtimeGeneration,
                recipe == null ? 0D : recipe.successRate(), deliveryClaim, deliveryRollback, deliveryCommit);
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge,
                                                            long runtimeGeneration,
                                                            double successRate,
                                                            BooleanSupplier deliveryClaim,
                                                            Runnable deliveryRollback,
                                                            Runnable deliveryCommit) {
        RequestPermit permit = acquire(runtimeGeneration);
        if (permit == null) {
            plugin.runtimeMetrics().recordExecutionStale();
            return CompletableFuture.completedFuture(unavailableResult("forge runtime is not accepting requests"));
        }
        try {
            ValidationResult validation = validationService.canForge(player, recipe, guiItems);
            long sessionGeneration = player == null
                    ? 0L
                    : plugin.playerDataStore().ensureCurrentGeneration(player.getUniqueId());
            if (player != null && !plugin.playerDataStore().isSessionWritable(player.getUniqueId())) {
                permit.close();
                ForgeResult result = unavailableResult("player data is not ready");
                return CompletableFuture.completedFuture(result);
            }
            CompletableFuture<ForgeResult> execution = forgeExecutionService.execute(
                    player,
                    recipe,
                    guiItems,
                    preparedForge,
                    validation,
                    successRate,
                    sessionGeneration,
                    runtimeGeneration,
                    deliveryClaim,
                    deliveryRollback,
                    deliveryCommit
            );
            if (execution == null) {
                permit.close();
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Forge execution returned no completion future."));
            }
            return execution.whenComplete((ignored, throwable) -> permit.close());
        } catch (Throwable throwable) {
            permit.close();
            return CompletableFuture.failedFuture(throwable);
        }
    }

    public boolean trackCompletion(long generation, CompletionStage<?> completion) {
        if (completion == null) {
            return false;
        }
        RequestPermit permit = acquire(generation);
        if (permit == null) {
            return false;
        }
        try {
            completion.whenComplete((ignored, throwable) -> permit.close());
            return true;
        } catch (Throwable throwable) {
            permit.close();
            return false;
        }
    }

    public String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        return resultItemFactory.resolveResultItemName(recipe, itemStack);
    }

    private List<ForgeMaterial.QualityModifier> resolveMaterialQualityModifiers(Recipe recipe, GuiItems guiItems) {
        if (recipe == null || guiItems == null) {
            return List.of();
        }
        return layerSnapshotBuilder.collectQualityModifiers(layerSnapshotBuilder.collectMaterialContributions(null, recipe, guiItems));
    }

    private boolean isPreparationThreadOwned(Player player) {
        if (threadOwnership == null) {
            return true;
        }
        return player == null ? threadOwnership.isGlobalOwned() : threadOwnership.isEntityOwned(player);
    }

    private RequestPermit acquire(long generation) {
        if (!accepting.get() || !plugin.isRuntimeReady() || !plugin.isGenerationActive(generation)) {
            return null;
        }
        String permitId = UUID.randomUUID().toString();
        operationJournal.begin(permitId, "forge", null, null);
        if (!accepting.get() || !plugin.isRuntimeReady() || !plugin.isGenerationActive(generation)) {
            operationJournal.archive(permitId);
            return null;
        }
        return new RequestPermit(permitId);
    }

    private void release(String permitId) {
        operationJournal.archive(permitId);
    }

    private ForgeResult unavailableResult(String reason) {
        ForgeResult result = new ForgeResult();
        result.setErrorKey("forge.error.runtime_unavailable");
        result.setReplacements(Map.of("reason", reason, "state",
                plugin.runtimeStatus().name().toLowerCase(Locale.ROOT)));
        return result;
    }

    private final class RequestPermit implements AutoCloseable {
        private final String permitId;
        private final AtomicBoolean closed = new AtomicBoolean();

        RequestPermit(String permitId) {
            this.permitId = permitId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(permitId);
            }
        }
    }
}
