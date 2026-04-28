package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.cache.CacheManager;
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
        return measure("assembly-preview", () -> {
            String cacheKey = requestSignature(request);
            ItemStack cached = previewCache.get(cacheKey);
            if (cached != null) {
                return cached.clone();
            }
            ItemStack rendered = renderPreview(request);
            if (rendered != null) {
                previewCache.put(cacheKey, rendered.clone());
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
        String baseCustomName = resolveBaseCustomName(request.existingItem(), existingIsEmakiItem);
        if (existingIsEmakiItem) {
            if (baseSource == null) {
                baseSource = dataManager.readBaseSource(request.existingItem());
            }
            if (request.amount() <= 0) {
                amount = dataManager.readBaseAmount(request.existingItem());
            }
            previousActiveLayers = dataManager.readActiveLayers(request.existingItem());
            mergedLayers.putAll(dataManager.readLayerSnapshots(request.existingItem()));
        }
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
        if (baseSource == null && request.existingItem() != null && !request.existingItem().getType().isAir()) {
            baseSource = itemSourceService.identifyItem(request.existingItem());
        }
        if (baseSource == null) {
            return null;
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
                orderedLayers.values().stream().map(EmakiItemLayerSnapshot::toMap).toList()
        ));
        return new AssemblyContext(baseSource, Math.max(1, amount), baseCustomName, orderedLayers, activeLayers, previousActiveLayers, signature);
    }

    private ItemStack renderPreview(EmakiItemAssemblyRequest request) {
        AssemblyContext context = resolveContext(request);
        if (context == null || context.baseSource() == null) {
            return null;
        }
        ItemStack itemStack = itemSourceService.createItem(context.baseSource(), context.amount());
        if (itemStack == null) {
            return null;
        }
        itemRenderService.renderItem(itemStack, context.layerSnapshots().values(), baseNameOverride(context.baseCustomName()));
        dataManager.writeAssemblyData(
                itemStack,
                CURRENT_SCHEMA_VERSION,
                context.baseSource(),
                context.amount(),
                context.baseCustomName(),
                context.activeLayers(),
                context.previousActiveLayers(),
                context.assemblySignature(),
                context.layerSnapshots().values()
        );
        return itemStack;
    }

    private String requestSignature(EmakiItemAssemblyRequest request) {
        if (request == null) {
            return "assembly:null";
        }
        return SignatureUtil.stableSignature(List.of(
                request.baseSource() == null ? "" : ItemSourceUtil.toShorthand(request.baseSource()),
                request.amount(),
                request.existingItem() == null ? "" : ItemSourceUtil.toShorthand(itemSourceService.identifyItem(request.existingItem())),
                request.existingItem() == null ? "" : resolveBaseCustomName(request.existingItem(), dataManager.isEmakiItem(request.existingItem())),
                request.layerSnapshots() == null ? List.of() : request.layerSnapshots().stream().map(EmakiItemLayerSnapshot::toMap).toList(),
                request.removedNamespaceIds()
        ));
    }

    private String resolveBaseCustomName(ItemStack existingItem, boolean existingIsEmakiItem) {
        if (existingItem == null) {
            return "";
        }
        if (existingIsEmakiItem) {
            return dataManager.readBaseCustomName(existingItem);
        }
        ItemMeta itemMeta = existingItem.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
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
            Map<String, EmakiItemLayerSnapshot> layerSnapshots,
            List<String> activeLayers,
            List<String> previousActiveLayers,
            String assemblySignature) {

    }
}

