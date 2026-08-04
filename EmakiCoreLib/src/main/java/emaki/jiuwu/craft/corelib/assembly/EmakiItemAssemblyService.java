package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;


import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.cache.CacheManager;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;
import emaki.jiuwu.craft.corelib.api.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;

public final class EmakiItemAssemblyService {

    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final int PREVIEW_CACHE_SIZE = 128;
    // Was Action.DEFAULT_TIMEOUT_MILLIS before the v1 action system was removed. The value is inlined
    // rather than re-pointed at a v2 constant: a preview cache TTL has nothing to do with how long a
    // pipeline stage may run, and borrowing that number again would recreate the same false coupling.
    private static final long PREVIEW_CACHE_TTL_MILLIS = 30_000L;
    // Diagnostics below read EmakiItem's set-state fields. The partition must mirror EmakiItem's write
    // side exactly (EmakiItemIdentifier.PARTITION = "emakiitem" over PdcService namespace "emaki",
    // wired in ItemLifecycleCoordinator), otherwise existing item data becomes invisible here.
    private static final PdcPartition ITEM_SET_PARTITION = new PdcPartition("emaki", "emakiitem");

    private final ItemSourceService itemSourceService;
    private final AssemblyDataManager dataManager;
    private final EmakiNamespaceRegistry namespaceRegistry;
    private final ItemRenderService itemRenderService;
    private final ItemOperationLedger operationLedger;
    private final ItemLoreReconciler loreReconciler = new ItemLoreReconciler();
    private final CacheManager<String, ManagedProjection> previewCache =
            new CacheManager<>(PREVIEW_CACHE_SIZE, PREVIEW_CACHE_TTL_MILLIS);
    private volatile AsyncConfig asyncConfig = new AsyncConfig(null, null, null, null);

    public EmakiItemAssemblyService(EmakiNamespaceRegistry namespaceRegistry,
            EmakiItemLayerCodecRegistry codecRegistry,
            ItemSourceService itemSourceService) {
        this(namespaceRegistry, codecRegistry, itemSourceService, null);
    }

    public EmakiItemAssemblyService(EmakiNamespaceRegistry namespaceRegistry,
            EmakiItemLayerCodecRegistry codecRegistry,
            ItemSourceService itemSourceService,
            DebugLogger debugLogger) {
        this.namespaceRegistry = Objects.requireNonNull(namespaceRegistry, "namespaceRegistry");
        this.itemSourceService = Objects.requireNonNull(itemSourceService, "itemSourceService");
        this.dataManager = new AssemblyDataManager(namespaceRegistry, codecRegistry, debugLogger);
        this.itemRenderService = new ItemRenderService(namespaceRegistry);
        this.operationLedger = new ItemOperationLedger(debugLogger);
    }

    public void configureAsync(AsyncTaskScheduler asyncTaskScheduler,
            ExecutionDispatcher executionDispatcher,
            Plugin executionOwner,
            PerformanceMonitor performanceMonitor) {
        this.asyncConfig = new AsyncConfig(asyncTaskScheduler, executionDispatcher, executionOwner, performanceMonitor);
    }

    public ItemStack preview(EmakiItemAssemblyRequest request) {
        return preview(request, "direct", null);
    }

    public ItemStack preview(EmakiItemAssemblyRequest request, String debugTarget, DebugLogger debugLogger) {
        return preview(request, debugTarget, debugLogger, null);
    }

    public ItemStack preview(EmakiItemAssemblyRequest request, ItemOperationLedger.ReadResult readResult) {
        return preview(request, "direct", null, readResult);
    }

    private ItemStack preview(EmakiItemAssemblyRequest request,
                              String debugTarget,
                              DebugLogger debugLogger,
                              ItemOperationLedger.ReadResult suppliedReadResult) {
        return measure("assembly-preview", () -> {
            ItemStack existingItem = request == null ? null : request.existingItem();
            ItemOperationLedger.ReadResult readResult = existingItem == null
                    ? ItemOperationLedger.ReadResult.absent()
                    : suppliedReadResult == null ? operationLedger.read(existingItem) : suppliedReadResult;
            UUID playerId = request == null ? null : request.feedbackPlayerId();
            boolean debugEnabled = debugLogger != null && debugLogger.shouldLog("forge", playerId);
            if (debugEnabled) {
                Map<String, Object> replacements = debugReplacements(
                        "target", debugTarget,
                        "request_layers", request == null ? List.of() : request.layerSnapshots().stream()
                                .map(EmakiItemLayerSnapshot::namespaceId).toList(),
                        "removed", request == null ? List.of() : request.removedNamespaceIds()
                );
                putItemState(replacements, existingItem, readResult);
                debugAssembly(debugLogger, playerId, "common.assembly.input", replacements);
            }

            AssemblyContext context = resolveContext(request, readResult);
            if (context == null || context.baseSource() == null) {
                if (debugEnabled) {
                    debugAssembly(debugLogger, playerId, "common.assembly.output_no_context",
                            debugReplacements("target", debugTarget));
                }
                return null;
            }
            if (debugEnabled) {
                debugAssembly(debugLogger, playerId, "common.assembly.context", debugReplacements(
                        "target", debugTarget,
                        "base", ItemSourceUtil.toShorthand(context.baseSource()),
                        "amount", context.amount(),
                        "base_lore", context.baseLore().size(),
                        "active_layers", context.activeLayers(),
                        "previous_layers", context.previousActiveLayers(),
                        "operations", operationIds(context.operationEntries()),
                        "assembly_signature", context.assemblySignature()
                ));
            }

            String source = "cache";
            ManagedProjection managed = previewCache.get(context.assemblySignature());
            if (managed != null) {
                managed = managed.copy();
                if (!managedCacheValid(managed, context)) {
                    previewCache.invalidate(context.assemblySignature());
                    managed = null;
                    source = "render_after_invalid_cache";
                }
            }
            if (managed == null) {
                if (!"render_after_invalid_cache".equals(source)) {
                    source = "render";
                }
                managed = renderManagedProjection(context);
                if (managed == null || !managedCacheValid(managed, context)) {
                    if (debugEnabled) {
                        debugAssembly(debugLogger, playerId, "common.assembly.output_render_invalid",
                                debugReplacements(
                                        "target", debugTarget,
                                        "expected", operationIds(context.operationEntries())
                                ));
                    }
                    return null;
                }
                previewCache.put(context.assemblySignature(), managed.copy());
            }

            ItemStack result = commitInstance(context, managed);
            if (result == null) {
                if (debugEnabled) {
                    debugAssembly(debugLogger, playerId, "common.assembly.output_commit_failed",
                            debugReplacements("target", debugTarget));
                }
                return null;
            }
            if (debugEnabled) {
                Map<String, Object> replacements = debugReplacements(
                        "target", debugTarget,
                        "source", source
                );
                putItemState(replacements, result, managed.readResult());
                debugAssembly(debugLogger, playerId, "common.assembly.output", replacements);
            }
            return result;
        });
    }

    public CompletableFuture<ItemStack> previewAsync(EmakiItemAssemblyRequest request) {
        AsyncConfig config = asyncConfig;
        if (config.scheduler() == null) {
            return CompletableFuture.completedFuture(preview(request));
        }
        CompletableFuture<ItemStack> rendered = config.scheduler().supplyAsync(
                "assembly-preview",
                AsyncTaskScheduler.TaskPriority.NORMAL,
                10_000L,
                () -> preview(request));
        if (config.executionDispatcher() == null || config.executionOwner() == null) {
            return rendered.thenApply(itemStack -> itemStack == null ? null : itemStack.clone());
        }
        return rendered.thenCompose(itemStack -> config.executionDispatcher().submitGlobal(
                config.executionOwner(),
                () -> itemStack == null ? null : itemStack.clone()));
    }

    public ItemStack rebuild(ItemStack itemStack) {
        if (!isEmakiItem(itemStack)) {
            return itemStack == null ? null : itemStack.clone();
        }
        return preview(new EmakiItemAssemblyRequest(null, 0, itemStack, List.of()));
    }

    public ItemStack give(Player player, EmakiItemAssemblyRequest request) {
        EmakiItemAssemblyRequest effectiveRequest = request == null
                ? null
                : request.withFeedbackPlayerId(player == null ? null : player.getUniqueId());
        ItemStack itemStack = preview(effectiveRequest);
        if (player == null || itemStack == null) {
            return itemStack;
        }
        deliverToPlayer(player, itemStack);
        return itemStack;
    }

    public CompletableFuture<ItemStack> giveAsync(Player player, EmakiItemAssemblyRequest request) {
        EmakiItemAssemblyRequest effectiveRequest = request == null
                ? null
                : request.withFeedbackPlayerId(player == null ? null : player.getUniqueId());
        return previewAsync(effectiveRequest).thenCompose(itemStack -> deliverToPlayerAsync(player, itemStack));
    }

    public boolean isEmakiItem(ItemStack itemStack) {
        return dataManager.isEmakiItem(itemStack);
    }

    public ItemSourceRef readBaseSource(ItemStack itemStack) {
        return dataManager.readBaseSource(itemStack);
    }

    public int readBaseAmount(ItemStack itemStack) {
        return dataManager.readBaseAmount(itemStack);
    }

    public List<String> readActiveLayers(ItemStack itemStack) {
        return dataManager.readActiveLayers(itemStack);
    }

    public Map<String, EmakiItemLayerSnapshot> readLayerSnapshots(ItemStack itemStack) {
        return dataManager.readLayerSnapshots(itemStack);
    }

    public EmakiItemLayerSnapshot readLayerSnapshot(ItemStack itemStack, String namespaceId) {
        return dataManager.readLayerSnapshot(itemStack, namespaceId);
    }

    public ItemStack removeLayer(ItemStack itemStack, String namespaceId) {
        if (itemStack == null || !isEmakiItem(itemStack)) {
            return itemStack == null ? null : itemStack.clone();
        }
        return preview(new EmakiItemAssemblyRequest(null, 0, itemStack, List.of(), List.of(namespaceId)));
    }

    public ItemStack removeLayers(ItemStack itemStack, List<String> namespaceIds) {
        if (itemStack == null || !isEmakiItem(itemStack)) {
            return itemStack == null ? null : itemStack.clone();
        }
        return preview(new EmakiItemAssemblyRequest(null, 0, itemStack, List.of(), namespaceIds));
    }

    public void clearPreviewCache() {
        previewCache.clear();
    }

    private CompletableFuture<ItemStack> deliverToPlayerAsync(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null) {
            return CompletableFuture.completedFuture(itemStack);
        }
        AsyncConfig config = asyncConfig;
        if (config.executionDispatcher() == null || config.executionOwner() == null) {
            if (config.scheduler() != null) {
                return CompletableFuture.failedFuture(new RejectedExecutionException(
                        "Async assembly delivery requires an ExecutionDispatcher and owner."));
            }
            deliverToPlayer(player, itemStack);
            return CompletableFuture.completedFuture(itemStack);
        }
        CompletableFuture<ItemStack> future = new CompletableFuture<>();
        Runnable retired = () -> future.completeExceptionally(new RejectedExecutionException(
                "Assembly delivery target retired before item could be delivered."));
        try {
            if (config.executionDispatcher().runEntity(config.executionOwner(), player, () -> {
                try {
                    deliverToPlayer(player, itemStack);
                    future.complete(itemStack);
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            }, retired) == null) {
                retired.run();
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private void deliverToPlayer(Player player, ItemStack itemStack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack.clone());
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private AssemblyContext resolveContext(EmakiItemAssemblyRequest request,
                                           ItemOperationLedger.ReadResult suppliedReadResult) {
        if (request == null) {
            return null;
        }
        ItemStack existingItem = request.existingItem();
        ItemOperationLedger.ReadResult readResult = suppliedReadResult == null
                ? ItemOperationLedger.ReadResult.corrupt(List.of())
                : suppliedReadResult;
        if (readResult.corrupt()) {
            return null;
        }
        boolean existingIsEmakiItem = existingItem != null && dataManager.isEmakiItem(existingItem);
        List<ItemOperationEntry> operationEntries = readResult.entries();
        Map<String, EmakiItemLayerSnapshot> mergedLayers = new LinkedHashMap<>();
        Map<String, EmakiItemLayerSnapshot> storedLayers = Map.of();
        List<String> previousActiveLayers = List.of();

        ItemSourceRef storedBaseSource = existingIsEmakiItem ? dataManager.readBaseSource(existingItem) : null;
        int storedAmount = existingIsEmakiItem
                ? dataManager.readBaseAmount(existingItem)
                : existingItem == null ? 1 : Math.max(1, existingItem.getAmount());
        String storedBaseCustomName = existingIsEmakiItem ? dataManager.readBaseCustomName(existingItem) : "";
        List<String> storedBaseLore = existingIsEmakiItem ? safeLore(dataManager.readBaseLore(existingItem)) : List.of();

        ItemSourceRef baseSource = request.baseSource() != null ? request.baseSource() : storedBaseSource;
        int amount = request.amount() > 0 ? request.amount() : storedAmount;
        String baseCustomName;
        List<String> baseLore;
        ItemPresentationSnapshot previousPresentation;

        if (existingIsEmakiItem) {
            previousActiveLayers = dataManager.readActiveLayers(existingItem);
            storedLayers = dataManager.readLayerSnapshots(existingItem);
            mergedLayers.putAll(storedLayers);
            baseCustomName = storedBaseCustomName;
            baseLore = storedBaseLore;
            previousPresentation = dataManager.readPresentationSnapshot(existingItem);
            if (previousPresentation == null) {
                previousPresentation = renderPresentationSnapshot(
                        storedBaseSource,
                        storedAmount,
                        storedBaseCustomName,
                        storedBaseLore,
                        storedLayers,
                        operationEntries
                );
                if (previousPresentation == null) {
                    return null;
                }
            }
        } else if (existingItem != null && !operationEntries.isEmpty()) {
            ItemOperationBaseView baseView = operationLedger.resolveBaseView(existingItem, operationEntries);
            baseCustomName = baseView.customName();
            baseLore = baseView.lore();
            ItemOperationReplayer.ReplayResult projection = operationLedger.renderFromBase(
                    existingItem,
                    baseView,
                    operationEntries
            );
            previousPresentation = capturePresentation(projection.itemStack());
            if (previousPresentation == null) {
                return null;
            }
        } else {
            baseCustomName = currentCustomName(existingItem);
            baseLore = currentLore(existingItem);
            previousPresentation = existingItem == null
                    ? new ItemPresentationSnapshot("", List.of())
                    : new ItemPresentationSnapshot(baseCustomName, baseLore);
        }

        if (baseSource == null && existingItem != null && !existingItem.getType().isAir()) {
            baseSource = itemSourceService.identifyItem(existingItem);
        }
        if (baseSource == null) {
            return null;
        }

        boolean previousManagedNameOverlay = hasManagedNameOverlay(storedLayers, operationEntries);
        for (String namespaceId : request.removedNamespaceIds()) {
            mergedLayers.remove(Texts.normalizeId(namespaceId));
        }
        for (EmakiItemLayerSnapshot snapshot : request.layerSnapshots()) {
            if (snapshot == null || Texts.isBlank(snapshot.namespaceId())) {
                continue;
            }
            mergedLayers.put(Texts.normalizeId(snapshot.namespaceId()), snapshot);
        }

        List<String> activeLayers = namespaceRegistry.orderNamespaces(mergedLayers.keySet());
        Map<String, EmakiItemLayerSnapshot> orderedLayers = new LinkedHashMap<>();
        for (String namespaceId : activeLayers) {
            EmakiItemLayerSnapshot snapshot = mergedLayers.get(namespaceId);
            if (snapshot != null) {
                orderedLayers.put(namespaceId, snapshot);
            }
        }
        boolean commitAssembly = existingIsEmakiItem
                || Texts.isNotBlank(baseCustomName)
                || !baseLore.isEmpty()
                || !operationEntries.isEmpty()
                || !orderedLayers.isEmpty();
        boolean assemblyNameOverlay = hasAssemblyNameOverlay(orderedLayers);
        boolean managedNameOverlay = assemblyNameOverlay || hasOperationNameOverlay(operationEntries);
        String signature = SignatureUtil.stableSignature(List.of(
                ItemSourceUtil.toShorthand(baseSource),
                Math.max(1, amount),
                baseCustomName,
                baseLore,
                orderedLayers.values().stream().map(EmakiItemLayerSnapshot::toMap).toList(),
                operationEntries.stream().map(ItemOperationEntry::toMap).toList()
        ));
        return new AssemblyContext(
                existingItem,
                existingIsEmakiItem,
                baseSource,
                Math.max(1, amount),
                baseCustomName,
                baseLore,
                orderedLayers,
                activeLayers,
                previousActiveLayers,
                operationEntries,
                previousPresentation,
                previousManagedNameOverlay,
                assemblyNameOverlay,
                managedNameOverlay,
                commitAssembly,
                signature
        );
    }

    private ManagedProjection renderManagedProjection(AssemblyContext context) {
        ItemStack itemStack = itemSourceService.createItem(context.baseSource(), context.amount());
        if (itemStack == null) {
            return null;
        }
        applyBaseLore(itemStack, context.baseLore());
        itemRenderService.renderItem(
                itemStack,
                context.layerSnapshots().values(),
                baseNameOverride(context.baseCustomName())
        );
        List<ItemOperationEntry> refreshedEntries = operationLedger.replay(itemStack, context.operationEntries());
        ItemOperationLedger.ReadResult refreshedReadResult = ItemOperationLedger.ReadResult.valid(refreshedEntries);
        ItemPresentationSnapshot snapshot = capturePresentation(itemStack, context.assemblyNameOverlay());
        if (snapshot == null) {
            return null;
        }
        if (context.commitAssembly()) {
            dataManager.writeAssemblyData(
                    itemStack,
                    CURRENT_SCHEMA_VERSION,
                    context.baseSource(),
                    context.amount(),
                    context.baseCustomName(),
                    context.baseLore(),
                    context.activeLayers(),
                    context.previousActiveLayers(),
                    context.assemblySignature(),
                    context.layerSnapshots().values()
            );
            operationLedger.replaceAll(itemStack, refreshedEntries);
            if (!dataManager.writePresentationSnapshot(itemStack, snapshot)) {
                return null;
            }
        }
        return new ManagedProjection(itemStack, refreshedReadResult);
    }

    private ItemStack commitInstance(AssemblyContext context, ManagedProjection managedProjection) {
        if (managedProjection == null || managedProjection.itemStack() == null
                || managedProjection.readResult().corrupt()) {
            return null;
        }
        ItemStack managedItem = managedProjection.itemStack();
        ItemStack result = managedItem.clone();
        ItemPresentationSnapshot managedSnapshot = capturePresentation(
                managedItem,
                context.assemblyNameOverlay()
        );
        if (managedSnapshot == null) {
            return null;
        }
        ItemOperationLedger.ReadResult managedReadResult = managedProjection.readResult();
        List<ItemOperationEntry> managedEntries = managedReadResult.entries();
        Set<String> knownLayerNamespaces = new LinkedHashSet<>(context.previousActiveLayers());
        knownLayerNamespaces.addAll(context.activeLayers());
        Set<NamespacedKey> expectedNonOwnedKeys = dataManager.nonOwnedKeys(
                context.existingItem(),
                knownLayerNamespaces
        );

        ItemLoreReconciler.Reconciliation reconciliation = context.existingItem() == null
                ? new ItemLoreReconciler.Reconciliation(managedSnapshot.lore(), List.of(), List.of())
                : loreReconciler.reconcile(
                        context.previousPresentation().lore(),
                        currentLore(context.existingItem()),
                        managedSnapshot.lore()
                );
        if (!loreReconciler.preservesExternalProjection(
                managedSnapshot.lore(),
                reconciliation.lore(),
                reconciliation.externalLines())) {
            return null;
        }
        ItemOperationLedger.CustomNameUpdate customNameUpdate = context.existingItem() == null
                ? new ItemOperationLedger.CustomNameUpdate(managedSnapshot.customName(), false, "")
                : operationLedger.prepareCustomNameUpdate(
                        context.existingItem(),
                        context.previousPresentation().customName(),
                        managedSnapshot.customName(),
                        context.previousManagedNameOverlay(),
                        context.managedNameOverlay()
                );

        dataManager.copyPersistentDataForCommit(context.existingItem(), result);
        if (context.commitAssembly()) {
            dataManager.writeAssemblyData(
                    result,
                    CURRENT_SCHEMA_VERSION,
                    context.baseSource(),
                    context.amount(),
                    context.baseCustomName(),
                    context.baseLore(),
                    context.activeLayers(),
                    context.previousActiveLayers(),
                    context.assemblySignature(),
                    context.layerSnapshots().values()
            );
            operationLedger.replaceAll(result, managedEntries);
            if (!dataManager.writePresentationSnapshot(result, managedSnapshot)) {
                return null;
            }
        }
        if (!writeDisplay(result, customNameUpdate.customName(), reconciliation.lore())) {
            return null;
        }
        operationLedger.writeCustomNameUpdate(result, customNameUpdate);
        return validateCommit(
                context,
                result,
                managedSnapshot,
                managedReadResult,
                expectedNonOwnedKeys,
                reconciliation
        ) ? result : null;
    }

    private boolean validateCommit(AssemblyContext context,
            ItemStack result,
            ItemPresentationSnapshot managedSnapshot,
            ItemOperationLedger.ReadResult managedReadResult,
            Set<NamespacedKey> expectedNonOwnedKeys,
            ItemLoreReconciler.Reconciliation reconciliation) {
        if (!dataManager.containsKeys(result, expectedNonOwnedKeys)) {
            return false;
        }
        if (!loreReconciler.preservesExternalProjection(
                managedSnapshot.lore(),
                currentLore(result),
                reconciliation.externalLines())) {
            return false;
        }
        if (!context.commitAssembly()) {
            return true;
        }
        if (!context.baseSource().equals(dataManager.readBaseSource(result))) {
            return false;
        }
        if (!context.activeLayers().equals(dataManager.readActiveLayers(result))) {
            return false;
        }
        if (managedReadResult == null || managedReadResult.corrupt()) {
            return false;
        }
        return managedSnapshot.equals(dataManager.readPresentationSnapshot(result));
    }

    private boolean managedCacheValid(ManagedProjection managedProjection, AssemblyContext context) {
        if (managedProjection == null || managedProjection.itemStack() == null
                || managedProjection.readResult().corrupt()
                || !operationIdentities(context.operationEntries()).equals(
                        operationIdentities(managedProjection.readResult().entries()))) {
            return false;
        }
        return !context.commitAssembly()
                || dataManager.readPresentationSnapshot(managedProjection.itemStack()) != null;
    }

    private ItemPresentationSnapshot renderPresentationSnapshot(ItemSourceRef baseSource,
            int amount,
            String baseCustomName,
            List<String> baseLore,
            Map<String, EmakiItemLayerSnapshot> layers,
            List<ItemOperationEntry> operationEntries) {
        if (baseSource == null) {
            return null;
        }
        ItemStack itemStack = itemSourceService.createItem(baseSource, Math.max(1, amount));
        if (itemStack == null) {
            return null;
        }
        applyBaseLore(itemStack, baseLore);
        itemRenderService.renderItem(
                itemStack,
                layers == null ? List.of() : layers.values(),
                baseNameOverride(baseCustomName)
        );
        operationLedger.replay(itemStack, operationEntries);
        return capturePresentation(itemStack, hasAssemblyNameOverlay(layers));
    }

    private ItemPresentationSnapshot capturePresentation(ItemStack itemStack) {
        return capturePresentation(itemStack, false);
    }

    private ItemPresentationSnapshot capturePresentation(ItemStack itemStack, boolean assemblyNameOverlay) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return new ItemPresentationSnapshot(
                currentCustomName(itemStack),
                currentLore(itemStack),
                assemblyNameOverlay
        );
    }

    private boolean hasManagedNameOverlay(Map<String, EmakiItemLayerSnapshot> layers,
            List<ItemOperationEntry> operationEntries) {
        return hasAssemblyNameOverlay(layers) || hasOperationNameOverlay(operationEntries);
    }

    private boolean hasAssemblyNameOverlay(Map<String, EmakiItemLayerSnapshot> layers) {
        if (layers == null) {
            return false;
        }
        for (EmakiItemLayerSnapshot snapshot : layers.values()) {
            EmakiStructuredPresentation presentation = snapshot == null ? null : snapshot.structuredPresentation();
            if (presentation == null) {
                continue;
            }
            if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                    && Texts.isNotBlank(presentation.baseNameTemplate())) {
                return true;
            }
            if (!presentation.nameContributions().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOperationNameOverlay(List<ItemOperationEntry> operationEntries) {
        if (operationEntries == null) {
            return false;
        }
        for (ItemOperationEntry entry : operationEntries) {
            if (entry != null && entry.nameRecords() != null && !entry.nameRecords().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean writeDisplay(ItemStack itemStack, String customName, List<String> lore) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        ItemTextBridge.customName(itemMeta, Texts.isBlank(customName) ? null : MiniMessages.parse(customName));
        ItemTextBridge.setLoreLines(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return true;
    }

    private void applyBaseLore(ItemStack itemStack, List<String> baseLore) {
        if (itemStack == null || baseLore == null || baseLore.isEmpty()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        ItemTextBridge.setLoreLines(itemMeta, baseLore);
        itemStack.setItemMeta(itemMeta);
    }

    private Component baseNameOverride(String baseCustomName) {
        return Texts.isBlank(baseCustomName) ? null : MiniMessages.parse(baseCustomName);
    }

    private List<String> currentLore(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    private String currentCustomName(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
    }

    private List<String> operationIds(List<ItemOperationEntry> entries) {
        return operationIdentities(entries);
    }

    private List<String> operationIdentities(List<ItemOperationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(Objects::nonNull)
                .map(entry -> entry.sourceNamespace() + ":" + entry.operationId())
                .toList();
    }

    private List<String> safeLore(List<String> lore) {
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    private void debugAssembly(DebugLogger debugLogger,
                               UUID playerId,
                               String langKey,
                               Map<String, ?> replacements) {
        debugLogger.log("forge", playerId, langKey, replacements);
    }

    private Map<String, Object> debugReplacements(Object... entries) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            replacements.put(Texts.toStringSafe(entries[index]), entries[index + 1]);
        }
        return replacements;
    }

    private void putItemState(Map<String, Object> replacements,
                              ItemStack itemStack,
                              ItemOperationLedger.ReadResult readResult) {
        boolean empty = itemStack == null || itemStack.getType().isAir();
        replacements.put("item_empty", empty);
        if (empty) {
            replacements.put("item_type", "");
            replacements.put("item_lore_lines", 0);
            replacements.put("item_set_signature", "");
            replacements.put("item_set_lore_lines", "");
            replacements.put("item_operations", List.of());
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        replacements.put("item_type", itemStack.getType());
        replacements.put("item_lore_lines", currentLore(itemStack).size());
        replacements.put("item_set_signature", pdcString(itemMeta, "set_signature"));
        replacements.put("item_set_lore_lines", Objects.toString(pdcInteger(itemMeta, "set_lore_lines"), ""));
        replacements.put("item_operations", readResult == null || readResult.corrupt()
                ? "corrupt"
                : operationIds(readResult.entries()));
    }

    private String pdcString(ItemMeta itemMeta, String field) {
        if (itemMeta == null || Texts.isBlank(field)) {
            return "";
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        NamespacedKey key = ITEM_SET_PARTITION.key(field);
        if (!container.has(key, PersistentDataType.STRING)) {
            return "";
        }
        return Texts.toStringSafe(container.get(key, PersistentDataType.STRING));
    }

    private Integer pdcInteger(ItemMeta itemMeta, String field) {
        if (itemMeta == null || Texts.isBlank(field)) {
            return null;
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        NamespacedKey key = ITEM_SET_PARTITION.key(field);
        if (!container.has(key, PersistentDataType.INTEGER)) {
            return null;
        }
        return container.get(key, PersistentDataType.INTEGER);
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
            PerformanceMonitor monitor = asyncConfig.monitor();
            if (monitor != null) {
                monitor.record(metricKey, System.nanoTime() - startedAt, success);
            }
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record AsyncConfig(AsyncTaskScheduler scheduler,
            ExecutionDispatcher executionDispatcher,
            Plugin executionOwner,
            PerformanceMonitor monitor) {
    }

    private record ManagedProjection(ItemStack itemStack, ItemOperationLedger.ReadResult readResult) {

        private ManagedProjection {
            readResult = readResult == null
                    ? ItemOperationLedger.ReadResult.corrupt(List.of())
                    : readResult;
        }

        private ManagedProjection copy() {
            return new ManagedProjection(itemStack == null ? null : itemStack.clone(), readResult);
        }
    }

    private record AssemblyContext(ItemStack existingItem,
            boolean existingIsEmakiItem,
            ItemSourceRef baseSource,
            int amount,
            String baseCustomName,
            List<String> baseLore,
            Map<String, EmakiItemLayerSnapshot> layerSnapshots,
            List<String> activeLayers,
            List<String> previousActiveLayers,
            List<ItemOperationEntry> operationEntries,
            ItemPresentationSnapshot previousPresentation,
            boolean previousManagedNameOverlay,
            boolean assemblyNameOverlay,
            boolean managedNameOverlay,
            boolean commitAssembly,
            String assemblySignature) {
    }
}
