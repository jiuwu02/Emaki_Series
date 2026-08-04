package emaki.jiuwu.craft.station.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.config.StorageSettings;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.storage.api.EmakiStorageApi;
import emaki.jiuwu.craft.storage.api.model.StorageBatchOp;
import emaki.jiuwu.craft.storage.api.model.StorageBatchRequest;
import emaki.jiuwu.craft.storage.api.model.StorageBatchResult;

/**
 * Reads and debits materials straight from the player's EmakiStorage warehouse.
 *
 * <h2>Two constraints that shape every method here</h2>
 * <ul>
 *   <li><strong>Completion threads carry no guarantee.</strong> EmakiStorage's futures may complete on any
 *       thread. Nothing in this class touches a player, inventory, or GUI inside a continuation; callers
 *       that need to do so must hop back to the owner thread themselves.</li>
 *   <li><strong>Identity is full {@link ItemStack#equals(Object)}.</strong> Components, enchantments, and
 *       PDC all count. A template built here must be byte-for-byte equal to the one in the warehouse or the
 *       count comes back as zero, so every template is built through CoreLib's item-source service rather
 *       than assembled by hand.</li>
 * </ul>
 *
 * <p>Every warehouse call is gated on a capability check. Without
 * {@link StationCapabilities#storageChannelSupported()} this class refuses to act rather than falling back
 * to per-item withdrawals, which would route materials through the player's inventory.
 */
public final class StorageChannel {

    private final ItemSourceService itemSourceService;
    private final StationCapabilities capabilities;
    private final StorageSettings settings;

    /**
     * Creates the channel.
     *
     * @param itemSourceService CoreLib's item-source service, used to build warehouse-equal templates
     * @param capabilities      the capability probe result from enable time
     * @param settings          the warehouse settings from {@code config.yml}
     */
    public StorageChannel(ItemSourceService itemSourceService,
            StationCapabilities capabilities,
            StorageSettings settings) {
        this.itemSourceService = itemSourceService;
        this.capabilities = capabilities == null ? StationCapabilities.none() : capabilities;
        this.settings = settings == null ? StorageSettings.defaults() : settings;
    }

    /**
     * {@return whether the warehouse channel can be used right now}
     *
     * <p>True only when the configuration enables it, EmakiStorage reports itself usable, and the atomic
     * batch capability is present.
     */
    public boolean usable() {
        return settings.enabled()
                && capabilities.storageChannelSupported()
                && EmakiStorageApi.status().usable();
    }

    /**
     * Counts the warehouse stock backing a recipe's requirements.
     *
     * <p>Uses one multi-template round trip when the warehouse supports it, otherwise falls back to one
     * call per identity. The fallback is correct but its round-trip count grows with the requirement count,
     * so it is only a convenience, not a target state.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param playerId the warehouse owner
     * @param recipe   the recipe whose identities should be counted
     * @return a future carrying the counts per identity; empty on failure
     */
    public CompletableFuture<Map<ItemSourceRef, Long>> countAsync(UUID playerId, RecipeDefinition recipe) {
        if (playerId == null || recipe == null || !usable() || itemSourceService == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        Map<ItemSourceRef, ItemStack> templates = templatesFor(recipe);
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

    /**
     * Debits a recipe's requirements from the warehouse in one atomic batch.
     *
     * <p>All-or-nothing at the warehouse level: a single failed pre-check leaves every stored amount
     * exactly as it was, so a failed submission never has to be compensated.
     *
     * <p><strong>Thread:</strong> any thread. Requires the owner online. The future carries no
     * completion-thread guarantee.
     *
     * @param playerId the warehouse owner
     * @param recipe   the recipe being crafted
     * @param batch    how many times to apply the recipe
     * @return a future carrying the debited materials, or an explicit failure
     */
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

    /**
     * Deposits items into the warehouse in one atomic batch.
     *
     * <p><strong>Thread:</strong> any thread. Requires the owner online. The future carries no
     * completion-thread guarantee.
     *
     * @param playerId the warehouse owner
     * @param amounts  the units to deposit per identity
     * @return a future carrying how many units of each identity were accepted; empty on failure
     */
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
                planned.add(new ConsumedMaterial(source, taken, MaterialChannel.STORAGE));
            }
            if (needed > 0L) {
                return false;
            }
        }
        return true;
    }

    private Map<ItemSourceRef, ItemStack> templatesFor(RecipeDefinition recipe) {
        Map<ItemSourceRef, ItemStack> templates = new LinkedHashMap<>();
        for (MaterialRequirement requirement : recipe.requirements()) {
            for (ItemSourceRef source : requirement.sources()) {
                if (templates.containsKey(source)) {
                    continue;
                }
                ItemStack template = itemSourceService.createItem(source, 1);
                if (template != null && !template.getType().isAir()) {
                    templates.put(source, template);
                }
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
        List<emaki.jiuwu.craft.storage.api.model.StorageAmount> amounts = batch.applied();
        for (int index = 0; index < order.size() && index < amounts.size(); index++) {
            accepted.put(order.get(index), Math.max(0L, amounts.get(index).applied()));
        }
        return Map.copyOf(accepted);
    }
}
