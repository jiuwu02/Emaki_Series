package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.cache.CacheManager;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

public final class EmakiItemAssemblyService {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int PREVIEW_CACHE_SIZE = 128;
    private static final long PREVIEW_CACHE_TTL_MILLIS = Action.DEFAULT_TIMEOUT_MILLIS;

    private final ItemSourceService itemSourceService;
    private final AssemblyDataManager dataManager;
    private final EmakiNamespaceRegistry namespaceRegistry;
    private final ItemRenderService itemRenderService;
    private final ItemOperationLedger operationLedger = new ItemOperationLedger();
    private final CacheManager<String, ItemStack> previewCache =
            new CacheManager<>(PREVIEW_CACHE_SIZE, PREVIEW_CACHE_TTL_MILLIS);
    private volatile AsyncConfig asyncConfig = new AsyncConfig(null, null);

    public EmakiItemAssemblyService(EmakiNamespaceRegistry namespaceRegistry,
            EmakiItemLayerCodecRegistry codecRegistry,
            ItemSourceService itemSourceService) {
        this.namespaceRegistry = Objects.requireNonNull(namespaceRegistry, "namespaceRegistry");
        this.itemSourceService = Objects.requireNonNull(itemSourceService, "itemSourceService");
        this.dataManager = new AssemblyDataManager(namespaceRegistry, codecRegistry);
        this.itemRenderService = new ItemRenderService(namespaceRegistry);
    }

    public void configureAsync(AsyncTaskScheduler asyncTaskScheduler, PerformanceMonitor performanceMonitor) {
        this.asyncConfig = new AsyncConfig(asyncTaskScheduler, performanceMonitor);
    }

    public ItemStack preview(EmakiItemAssemblyRequest request) {
        return preview(request, "direct", null);
    }

    public ItemStack preview(EmakiItemAssemblyRequest request, String debugTarget, DebugLogger debugLogger) {
        return measure("assembly-preview", () -> {
            UUID playerId = request == null ? null : request.feedbackPlayerId();
            boolean debugEnabled = debugLogger != null && debugLogger.shouldLog("forge", playerId);
            if (debugEnabled) {
                debugAssembly(debugLogger, playerId, "[DEBUG:ASSEMBLY_INPUT]", debugTarget,
                        "request_layers=" + (request == null ? List.of() : request.layerSnapshots().stream()
                                .map(EmakiItemLayerSnapshot::namespaceId).toList())
                                + " removed=" + (request == null ? List.of() : request.removedNamespaceIds())
                                + " item=" + itemStateSummary(request == null ? null : request.existingItem()));
            }
            AssemblyContext context = resolveContext(request);
            if (context == null || context.baseSource() == null) {
                if (debugEnabled) {
                    debugAssembly(debugLogger, playerId, "[DEBUG:ASSEMBLY_OUTPUT]", debugTarget, "result=null reason=no_context");
                }
                return null;
            }
            if (debugEnabled) {
                debugAssembly(debugLogger, playerId, "[DEBUG:ASSEMBLY_CONTEXT]", debugTarget,
                        "base=" + ItemSourceUtil.toShorthand(context.baseSource())
                                + " amount=" + context.amount()
                                + " base_lore=" + context.baseLore().size()
                                + " active_layers=" + context.activeLayers()
                                + " previous_layers=" + context.previousActiveLayers()
                                + " operations=" + operationIds(context.operationEntries())
                                + " assembly_signature=" + shortValue(context.assemblySignature()));
            }
            String cacheKey = context.assemblySignature();
            ItemStack cached = previewCache.get(cacheKey);
            if (cached != null) {
                ItemStack result = cached.clone();
                if (debugEnabled) {
                    debugAssembly(debugLogger, playerId, "[DEBUG:ASSEMBLY_OUTPUT]", debugTarget,
                            "source=cache item=" + itemStateSummary(result));
                }
                return result;
            }
            ItemStack rendered = renderPreview(context);
            if (rendered != null) {
                previewCache.put(cacheKey, rendered.clone());
            }
            if (debugEnabled) {
                debugAssembly(debugLogger, playerId, "[DEBUG:ASSEMBLY_OUTPUT]", debugTarget,
                        "source=render item=" + itemStateSummary(rendered));
            }
            return rendered;
        });
    }

    public CompletableFuture<ItemStack> previewAsync(EmakiItemAssemblyRequest request) {
        AsyncConfig config = asyncConfig;
        if (config.scheduler() == null) {
            return CompletableFuture.completedFuture(preview(request));
        }
        return config.scheduler().supplyAsync("assembly-preview", AsyncTaskScheduler.TaskPriority.NORMAL, 10_000L, () -> preview(request))
                .thenCompose(rendered -> config.scheduler().callSync("assembly-preview-sync", () -> rendered == null ? null : rendered.clone()));
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
        return previewAsync(effectiveRequest).thenApply(itemStack -> {
            if (player == null || itemStack == null) {
                return itemStack;
            }
            deliverToPlayer(player, itemStack);
            return itemStack;
        });
    }

    public boolean isEmakiItem(ItemStack itemStack) {
        return dataManager.isEmakiItem(itemStack);
    }

    private void deliverToPlayer(Player player, ItemStack itemStack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack.clone());
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    public ItemSource readBaseSource(ItemStack itemStack) {
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

    private AssemblyContext resolveContext(EmakiItemAssemblyRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, EmakiItemLayerSnapshot> mergedLayers = new LinkedHashMap<>();
        ItemSource baseSource = request.baseSource();
        int amount = request.amount() > 0 ? request.amount() : 1;
        List<String> previousActiveLayers = List.of();
        boolean existingIsEmakiItem = request.existingItem() != null && dataManager.isEmakiItem(request.existingItem());
        Map<String, EmakiItemLayerSnapshot> storedLayers = Map.of();
        List<ItemOperationEntry> operationEntries = resolveOperationEntries(request.existingItem(), existingIsEmakiItem);
        if (existingIsEmakiItem) {
            if (baseSource == null) {
                baseSource = dataManager.readBaseSource(request.existingItem());
            }
            if (request.amount() <= 0) {
                amount = dataManager.readBaseAmount(request.existingItem());
            }
            previousActiveLayers = dataManager.readActiveLayers(request.existingItem());
            storedLayers = dataManager.readLayerSnapshots(request.existingItem());
            mergedLayers.putAll(storedLayers);
        }
        if (baseSource == null && request.existingItem() != null && !request.existingItem().getType().isAir()) {
            baseSource = itemSourceService.identifyItem(request.existingItem());
        }
        if (baseSource == null) {
            return null;
        }
        String baseCustomName = resolveBaseCustomName(request.existingItem(), existingIsEmakiItem, storedLayers, operationEntries);
        List<String> baseLore = resolveBaseLore(request.existingItem(), existingIsEmakiItem, baseSource, amount, storedLayers, operationEntries);
        for (String namespaceId : request.removedNamespaceIds()) {
            mergedLayers.remove(Texts.normalizeId(namespaceId));
        }
        if (request.layerSnapshots() != null) {
            for (EmakiItemLayerSnapshot snapshot : request.layerSnapshots()) {
                if (snapshot == null || Texts.isBlank(snapshot.namespaceId())) {
                    continue;
                }
                mergedLayers.put(Texts.normalizeId(snapshot.namespaceId()), snapshot);
            }
        }
        List<String> activeLayers = namespaceRegistry.orderNamespaces(mergedLayers.keySet());
        Map<String, EmakiItemLayerSnapshot> orderedLayers = new LinkedHashMap<>();
        for (String namespaceId : activeLayers) {
            EmakiItemLayerSnapshot snapshot = mergedLayers.get(namespaceId);
            if (snapshot != null) {
                orderedLayers.put(namespaceId, snapshot);
            }
        }
        String signature = SignatureUtil.stableSignature(List.of(
                ItemSourceUtil.toShorthand(baseSource),
                amount,
                baseCustomName,
                baseLore,
                orderedLayers.values().stream().map(EmakiItemLayerSnapshot::toMap).toList(),
                operationEntries.stream().map(ItemOperationEntry::toMap).toList()
        ));
        return new AssemblyContext(
                baseSource,
                Math.max(1, amount),
                baseCustomName,
                baseLore,
                orderedLayers,
                activeLayers,
                previousActiveLayers,
                operationEntries,
                signature
        );
    }

    private ItemStack renderPreview(AssemblyContext context) {
        ItemStack itemStack = itemSourceService.createItem(context.baseSource(), context.amount());
        if (itemStack == null) {
            return null;
        }
        if (!requiresRenderedAssembly(context)) {
            return itemStack;
        }
        applyBaseLore(itemStack, context.baseLore());
        itemRenderService.renderItem(itemStack, context.layerSnapshots().values(), baseNameOverride(context.baseCustomName()));
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
        operationLedger.replay(itemStack, context.operationEntries());
        return itemStack;
    }

    private boolean requiresRenderedAssembly(AssemblyContext context) {
        if (context == null) {
            return false;
        }
        if (Texts.isNotBlank(context.baseCustomName())) {
            return true;
        }
        if (context.baseLore() != null && !context.baseLore().isEmpty()) {
            return true;
        }
        if (context.operationEntries() != null && !context.operationEntries().isEmpty()) {
            return true;
        }
        if (context.layerSnapshots() == null || context.layerSnapshots().isEmpty()) {
            return false;
        }
        for (EmakiItemLayerSnapshot snapshot : context.layerSnapshots().values()) {
            if (snapshot != null && snapshot.hasStructuredPresentation()) {
                return true;
            }
        }
        return false;
    }

    private String resolveBaseCustomName(ItemStack existingItem,
            boolean existingIsEmakiItem,
            Map<String, EmakiItemLayerSnapshot> storedLayers,
            List<ItemOperationEntry> operationEntries) {
        if (existingItem == null) {
            return "";
        }
        String currentCustomName = currentCustomName(existingItem);
        if (!existingIsEmakiItem) {
            return currentCustomName;
        }
        String storedCustomName = dataManager.readBaseCustomName(existingItem);
        if (!hasEmakiNameOverlay(storedLayers, operationEntries)) {
            return currentCustomName.equals(storedCustomName) ? storedCustomName : currentCustomName;
        }
        return storedCustomName;
    }

    private String currentCustomName(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
    }

    private boolean hasEmakiNameOverlay(Map<String, EmakiItemLayerSnapshot> storedLayers,
            List<ItemOperationEntry> operationEntries) {
        if (storedLayers != null) {
            for (EmakiItemLayerSnapshot snapshot : storedLayers.values()) {
                EmakiStructuredPresentation presentation = snapshot == null ? null : snapshot.structuredPresentation();
                if (presentation == null) {
                    continue;
                }
                if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE && Texts.isNotBlank(presentation.baseNameTemplate())) {
                    return true;
                }
                if (!presentation.nameContributions().isEmpty()) {
                    return true;
                }
            }
        }
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

    private List<String> resolveBaseLore(ItemStack existingItem,
            boolean existingIsEmakiItem,
            ItemSource baseSource,
            int amount,
            Map<String, EmakiItemLayerSnapshot> storedLayers,
            List<ItemOperationEntry> operationEntries) {
        if (existingItem == null) {
            return List.of();
        }
        List<String> currentLore = currentLore(existingItem);
        if (!existingIsEmakiItem) {
            return currentLore;
        }
        List<String> storedLore = safeLore(dataManager.readBaseLore(existingItem));
        List<String> expectedLore = renderExpectedLore(baseSource, amount, storedLore, storedLayers, operationEntries);
        if (expectedLore == null) {
            return storedLore;
        }
        return inferMergedBaseLore(storedLore, expectedLore, currentLore);
    }

    private List<String> currentLore(ItemStack itemStack) {
        if (itemStack == null) {
            return List.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> loreLines = ItemTextBridge.loreLines(itemMeta);
        return safeLore(loreLines);
    }

    private List<String> renderExpectedLore(ItemSource baseSource,
            int amount,
            List<String> baseLore,
            Map<String, EmakiItemLayerSnapshot> storedLayers,
            List<ItemOperationEntry> operationEntries) {
        if (baseSource == null) {
            return null;
        }
        ItemStack expected = itemSourceService.createItem(baseSource, Math.max(1, amount));
        if (expected == null) {
            return null;
        }
        applyBaseLore(expected, baseLore);
        itemRenderService.renderItem(expected, storedLayers == null ? List.of() : storedLayers.values(), null);
        operationLedger.replay(expected, operationEntries);
        return currentLore(expected);
    }

    private List<String> inferMergedBaseLore(List<String> storedBaseLore, List<String> expectedLore, List<String> currentLore) {
        List<String> safeStored = safeLore(storedBaseLore);
        List<String> safeExpected = safeLore(expectedLore);
        List<String> safeCurrent = safeLore(currentLore);
        if (safeCurrent.equals(safeExpected)) {
            return safeStored;
        }
        if (safeExpected.isEmpty()) {
            return safeCurrent;
        }
        int[] leftMatches = leftmostSubsequenceMatches(safeExpected, safeCurrent);
        if (leftMatches == null) {
            return safeStored;
        }
        int[] rightMatches = rightmostSubsequenceMatches(safeExpected, safeCurrent);
        if (!sameMatches(leftMatches, rightMatches)) {
            return safeStored;
        }
        List<String> externalLines = new ArrayList<>();
        int matchIndex = 0;
        for (int index = 0; index < safeCurrent.size(); index++) {
            if (matchIndex < leftMatches.length && leftMatches[matchIndex] == index) {
                matchIndex++;
                continue;
            }
            externalLines.add(safeCurrent.get(index));
        }
        if (externalLines.isEmpty()) {
            return safeStored;
        }
        List<String> merged = new ArrayList<>(safeStored);
        merged.addAll(externalLines);
        return List.copyOf(merged);
    }

    private int[] leftmostSubsequenceMatches(List<String> expected, List<String> current) {
        int[] matches = new int[expected.size()];
        int currentIndex = 0;
        for (int expectedIndex = 0; expectedIndex < expected.size(); expectedIndex++) {
            String expectedLine = expected.get(expectedIndex);
            boolean matched = false;
            while (currentIndex < current.size()) {
                if (Objects.equals(expectedLine, current.get(currentIndex))) {
                    matches[expectedIndex] = currentIndex;
                    currentIndex++;
                    matched = true;
                    break;
                }
                currentIndex++;
            }
            if (!matched) {
                return null;
            }
        }
        return matches;
    }

    private int[] rightmostSubsequenceMatches(List<String> expected, List<String> current) {
        int[] matches = new int[expected.size()];
        int currentIndex = current.size() - 1;
        for (int expectedIndex = expected.size() - 1; expectedIndex >= 0; expectedIndex--) {
            String expectedLine = expected.get(expectedIndex);
            boolean matched = false;
            while (currentIndex >= 0) {
                if (Objects.equals(expectedLine, current.get(currentIndex))) {
                    matches[expectedIndex] = currentIndex;
                    currentIndex--;
                    matched = true;
                    break;
                }
                currentIndex--;
            }
            if (!matched) {
                return null;
            }
        }
        return matches;
    }

    private boolean sameMatches(int[] first, int[] second) {
        if (first == null || second == null || first.length != second.length) {
            return false;
        }
        for (int index = 0; index < first.length; index++) {
            if (first[index] != second[index]) {
                return false;
            }
        }
        return true;
    }

    private List<String> safeLore(List<String> loreLines) {
        return loreLines == null || loreLines.isEmpty() ? List.of() : List.copyOf(loreLines);
    }

    private List<ItemOperationEntry> resolveOperationEntries(ItemStack existingItem, boolean existingIsEmakiItem) {
        if (existingItem == null || !existingIsEmakiItem) {
            return List.of();
        }
        List<ItemOperationEntry> entries = operationLedger.readAll(existingItem);
        return entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    private void debugAssembly(DebugLogger debugLogger, UUID playerId, String anchor, String target, String details) {
        debugLogger.logRaw("forge", playerId, anchor
                + " target=" + Texts.toStringSafe(target)
                + " " + Texts.toStringSafe(details));
    }

    private String itemStateSummary(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "empty";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<ItemOperationEntry> entries = operationLedger.readAll(itemStack);
        return "type=" + itemStack.getType()
                + " lore=" + currentLore(itemStack).size()
                + " set_signature=" + shortValue(pdcString(itemMeta, "set_signature"))
                + " set_lore_lines=" + Objects.toString(pdcInteger(itemMeta, "set_lore_lines"), "-")
                + " operations=" + operationIds(entries);
    }

    private List<String> operationIds(List<ItemOperationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(Objects::nonNull)
                .map(entry -> entry.sourceNamespace() + ":" + entry.operationId())
                .toList();
    }

    private String pdcString(ItemMeta itemMeta, String field) {
        if (itemMeta == null || Texts.isBlank(field)) {
            return "";
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if (key.getKey().endsWith(field) && container.has(key, PersistentDataType.STRING)) {
                return Texts.toStringSafe(container.get(key, PersistentDataType.STRING));
            }
        }
        return "";
    }

    private Integer pdcInteger(ItemMeta itemMeta, String field) {
        if (itemMeta == null || Texts.isBlank(field)) {
            return null;
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if (key.getKey().endsWith(field) && container.has(key, PersistentDataType.INTEGER)) {
                return container.get(key, PersistentDataType.INTEGER);
            }
        }
        return null;
    }

    private String shortValue(String value) {
        String safe = Texts.toStringSafe(value);
        if (safe.isBlank()) {
            return "-";
        }
        return safe.length() <= 16 ? safe : safe.substring(0, 16);
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

    private record AsyncConfig(AsyncTaskScheduler scheduler, PerformanceMonitor monitor) {

    }

    private record AssemblyContext(ItemSource baseSource,
            int amount,
            String baseCustomName,
            List<String> baseLore,
            Map<String, EmakiItemLayerSnapshot> layerSnapshots,
            List<String> activeLayers,
            List<String> previousActiveLayers,
            List<ItemOperationEntry> operationEntries,
            String assemblySignature) {

    }
}
