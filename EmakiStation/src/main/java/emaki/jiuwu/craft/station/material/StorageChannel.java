package emaki.jiuwu.craft.station.material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.config.StorageSettings;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.storage.api.EmakiStorageApi;
import emaki.jiuwu.craft.storage.api.model.ReservationHandle;
import emaki.jiuwu.craft.storage.api.model.StorageAmount;
import emaki.jiuwu.craft.storage.api.model.StorageBatchOp;
import emaki.jiuwu.craft.storage.api.model.StorageBatchRequest;
import emaki.jiuwu.craft.storage.api.model.StorageBatchResult;

public final class StorageChannel {

    private final ItemSourceService itemSourceService;
    private final StationCapabilities capabilities;
    private final StorageSettings settings;

    public StorageChannel(ItemSourceService itemSourceService,
            StationCapabilities capabilities,
            StorageSettings settings) {
        this.itemSourceService = itemSourceService;
        this.capabilities = capabilities == null ? StationCapabilities.none() : capabilities;
        this.settings = settings == null ? StorageSettings.defaults() : settings;
    }

    public boolean usable() {
        return settings.enabled()
                && capabilities.storageChannelSupported()
                && EmakiStorageApi.status().usable();
    }

    public boolean reservationSupported() {
        return usable() && capabilities.reservation();
    }

    public CompletableFuture<Map<ItemSourceRef, Long>> countAsync(UUID playerId, RecipeDefinition recipe) {
        if (recipe == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return countSourcesAsync(playerId, sourcesOf(recipe));
    }

    public CompletableFuture<Map<ItemSourceRef, Long>> countSourcesAsync(UUID playerId,
            Collection<ItemSourceRef> sources) {
        if (playerId == null || sources == null || sources.isEmpty() || !usable()
                || itemSourceService == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        Map<ItemSourceRef, ItemStack> templates = templatesOf(sources);
        if (templates.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        if (capabilities.batchCount()) {
            return EmakiStorageApi.operations()
                    .countAllAsync(playerId, List.copyOf(templates.values()))
                    .thenApply(result -> mapCounts(templates, result));
        }
        return countIndividually(playerId, templates);
    }

    public CompletableFuture<EmakiResult<ReservationHandle>> reserveAsync(UUID playerId,
            Map<ItemSourceRef, Long> amounts,
            Duration ttl) {
        if (playerId == null || amounts == null || amounts.isEmpty() || itemSourceService == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("station.storage_bad_request"));
        }
        if (!reservationSupported()) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.storage_reservation_unavailable"));
        }
        List<StorageBatchOp> ops = withdrawalOps(amounts);
        if (ops.isEmpty()) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.storage_insufficient"));
        }
        if (ops.size() > settings.batchMaxOps()) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.storage_batch_too_large"));
        }
        return EmakiStorageApi.operations()
                .reserveAsync(playerId, StorageBatchRequest.atomic(ops), ttl);
    }

    public CompletableFuture<EmakiResult<Unit>> commitAsync(ReservationHandle handle) {
        if (handle == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("station.storage_bad_request"));
        }
        if (!usable()) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.storage_unavailable"));
        }
        return EmakiStorageApi.operations().commitAsync(handle)
                .thenApply(result -> result.isFailure() ? result.retypeFailure() : EmakiResult.ok());
    }

    public CompletableFuture<Void> releaseAsync(ReservationHandle handle) {
        if (handle == null || !usable()) {
            return CompletableFuture.completedFuture(null);
        }
        return EmakiStorageApi.operations().releaseAsync(handle).thenApply(ignored -> (Void) null);
    }

    public CompletableFuture<EmakiResult<List<ConsumedMaterial>>> consumeAmountsAsync(UUID playerId,
            Map<ItemSourceRef, Long> amounts) {
        if (playerId == null || amounts == null || amounts.isEmpty() || itemSourceService == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("station.storage_bad_request"));
        }
        if (!usable()) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.storage_unavailable"));
        }
        List<StorageBatchOp> ops = withdrawalOps(amounts);
        if (ops.isEmpty()) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.storage_insufficient"));
        }
        if (ops.size() > settings.batchMaxOps()) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.storage_batch_too_large"));
        }
        List<ConsumedMaterial> planned = new ArrayList<>();
        amounts.forEach((source, amount) -> {
            if (amount != null && amount > 0L) {
                planned.add(new ConsumedMaterial("legacy", "legacy", "legacy", source, -1,
                        MaterialChannel.STORAGE, amount, 0L));
            }
        });
        return EmakiStorageApi.operations()
                .applyBatchAsync(playerId, StorageBatchRequest.atomic(ops))
                .thenApply(result -> toConsumed(result, planned));
    }

    private List<StorageBatchOp> withdrawalOps(Map<ItemSourceRef, Long> amounts) {
        List<StorageBatchOp> ops = new ArrayList<>();
        for (Map.Entry<ItemSourceRef, Long> entry : amounts.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            ItemStack template = itemSourceService.createItem(entry.getKey(), 1);
            if (template == null || template.getType().isAir()) {
                return List.of();
            }
            ops.add(new StorageBatchOp(template, -entry.getValue()));
        }
        return ops;
    }

    public CompletableFuture<EmakiResult<List<ConsumedMaterial>>> consumeAsync(UUID playerId,
            RecipeDefinition recipe,
            long batch) {
        if (playerId == null || recipe == null || itemSourceService == null) {
            return CompletableFuture.completedFuture(EmakiResult.invalidInput("station.storage_bad_request"));
        }
        if (!usable()) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.storage_unavailable"));
        }
        return countAsync(playerId, recipe).thenCompose(available -> {
            List<StorageBatchOp> ops = new ArrayList<>();
            List<ConsumedMaterial> planned = new ArrayList<>();
            if (!planConsumption(recipe, batch, available, ops, planned)) {
                return CompletableFuture.completedFuture(
                        EmakiResult.<List<ConsumedMaterial>>rejected("station.storage_insufficient"));
            }
            if (ops.isEmpty()) {
                return CompletableFuture.completedFuture(
                        EmakiResult.<List<ConsumedMaterial>>success(List.of()));
            }
            if (ops.size() > settings.batchMaxOps()) {
                return CompletableFuture.completedFuture(
                        EmakiResult.<List<ConsumedMaterial>>rejected("station.storage_batch_too_large"));
            }
            return EmakiStorageApi.operations()
                    .applyBatchAsync(playerId, StorageBatchRequest.atomic(ops))
                    .thenApply(result -> toConsumed(result, planned));
        });
    }

    public CompletableFuture<Map<ItemSourceRef, Long>> depositAsync(UUID playerId,
            Map<ItemSourceRef, Long> amounts) {
        if (playerId == null || amounts == null || amounts.isEmpty() || !usable()
                || itemSourceService == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        List<StorageBatchOp> ops = new ArrayList<>();
        List<ItemSourceRef> order = new ArrayList<>();
        for (Map.Entry<ItemSourceRef, Long> entry : amounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            ItemStack template = itemSourceService.createItem(entry.getKey(), 1);
            if (template == null || template.getType().isAir()) {
                continue;
            }
            ops.add(new StorageBatchOp(template, entry.getValue()));
            order.add(entry.getKey());
        }
        if (ops.isEmpty() || ops.size() > settings.batchMaxOps()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return EmakiStorageApi.operations()
                .applyBatchAsync(playerId, StorageBatchRequest.bestEffort(ops))
                .thenApply(result -> depositedAmounts(result, order));
    }

    private boolean planConsumption(RecipeDefinition recipe,
            long batch,
            Map<ItemSourceRef, Long> available,
            List<StorageBatchOp> ops,
            List<ConsumedMaterial> planned) {
        Map<ItemSourceRef, Long> remaining = new LinkedHashMap<>(available);
        for (MaterialRequirement requirement : recipe.requirements()) {
            long needed = requirement.totalFor(batch);
            for (ItemSourceRef source : requirement.sources()) {
                if (needed <= 0L) {
                    break;
                }
                long have = remaining.getOrDefault(source, 0L);
                if (have <= 0L) {
                    continue;
                }
                long taken = Math.min(have, needed);
                remaining.put(source, have - taken);
                needed -= taken;
                if (!requirement.consume()) {
                    continue;
                }
                ItemStack template = itemSourceService.createItem(source, 1);
                if (template == null || template.getType().isAir()) {
                    return false;
                }
                ops.add(new StorageBatchOp(template, -taken));
                planned.add(new ConsumedMaterial(requirement.materialId(), requirement.requirementId(),
                        requirement.countKey(), source, -1, MaterialChannel.STORAGE, taken, 0L));
            }
            if (needed > 0L) {
                return false;
            }
        }
        return true;
    }

    public static List<ItemSourceRef> sourcesOf(RecipeDefinition recipe) {
        List<ItemSourceRef> sources = new ArrayList<>();
        for (MaterialRequirement requirement : recipe.requirements()) {
            for (ItemSourceRef source : requirement.sources()) {
                if (!sources.contains(source)) {
                    sources.add(source);
                }
            }
        }
        return sources;
    }

    private Map<ItemSourceRef, ItemStack> templatesOf(Collection<ItemSourceRef> sources) {
        Map<ItemSourceRef, ItemStack> templates = new LinkedHashMap<>();
        for (ItemSourceRef source : sources) {
            if (source == null || templates.containsKey(source)) {
                continue;
            }
            ItemStack template = itemSourceService.createItem(source, 1);
            if (template != null && !template.getType().isAir()) {
                templates.put(source, template);
            }
        }
        return templates;
    }

    private CompletableFuture<Map<ItemSourceRef, Long>> countIndividually(UUID playerId,
            Map<ItemSourceRef, ItemStack> templates) {
        Map<ItemSourceRef, Long> counts = new LinkedHashMap<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<ItemSourceRef, ItemStack> entry : templates.entrySet()) {
            chain = chain.thenCompose(ignored -> EmakiStorageApi.operations()
                    .countOfAsync(playerId, entry.getValue())
                    .thenAccept(result -> counts.put(entry.getKey(), result.orElse(0L))));
        }
        return chain.thenApply(ignored -> Map.copyOf(counts));
    }

    private static Map<ItemSourceRef, Long> mapCounts(Map<ItemSourceRef, ItemStack> templates,
            EmakiResult<Map<ItemStack, Long>> result) {
        Map<ItemStack, Long> counted = result.orElse(Map.of());
        if (counted == null || counted.isEmpty()) {
            return Map.of();
        }
        Map<ItemSourceRef, Long> mapped = new LinkedHashMap<>();
        templates.forEach((ref, template) -> mapped.put(ref, counted.getOrDefault(template, 0L)));
        return Map.copyOf(mapped);
    }

    private static EmakiResult<List<ConsumedMaterial>> toConsumed(EmakiResult<StorageBatchResult> result,
            List<ConsumedMaterial> planned) {
        if (result.isFailure()) {
            return result.retypeFailure();
        }
        return EmakiResult.success(List.copyOf(planned));
    }

    private static Map<ItemSourceRef, Long> depositedAmounts(EmakiResult<StorageBatchResult> result,
            List<ItemSourceRef> order) {
        if (result.isFailure()) {
            return Map.of();
        }
        StorageBatchResult batch = result.orElse(null);
        if (batch == null) {
            return Map.of();
        }
        Map<ItemSourceRef, Long> accepted = new LinkedHashMap<>();
        List<StorageAmount> amounts = batch.applied();
        for (int index = 0; index < order.size() && index < amounts.size(); index++) {
            accepted.put(order.get(index), Math.max(0L, amounts.get(index).applied()));
        }
        return Map.copyOf(accepted);
    }
}
