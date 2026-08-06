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

    /** {@return whether the warehouse can hold stock back instead of applying it immediately} */
    public boolean reservationSupported() {
        return usable() && capabilities.reservation();
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
        if (recipe == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return countSourcesAsync(playerId, sourcesOf(recipe));
    }

    /**
     * Counts the warehouse stock for an explicit identity set.
     *
     * <p>Exists so the merged channel can ask about exactly the identities it cares about without owning a
     * recipe, which is what lets one round trip serve a whole requirement list.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param playerId the warehouse owner
     * @param sources  the identities to count
     * @return a future carrying the counts per identity; empty on failure
     */
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

    /**
     * Holds an exact set of amounts without withdrawing them.
     *
     * <p>This is the half of the merged-channel debit that makes the whole operation recoverable. The
     * warehouse side is held first, the inventory side is debited synchronously afterwards, and a failure
     * there is undone by {@link #releaseAsync} — which cannot itself fail for lack of capacity, unlike
     * depositing already-withdrawn items back.
     *
     * <p><strong>Thread:</strong> any thread. Requires the owner online. The future carries no
     * completion-thread guarantee.
     *
     * @param playerId the warehouse owner
     * @param amounts  the units to hold per identity
     * @param ttl      how long the hold survives without a commit
     * @return a future carrying the ticket, or an explicit failure
     */
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

    /**
     * Applies a previously held reservation.
     *
     * <p><strong>Thread:</strong> any thread. Requires the owner online. The future carries no
     * completion-thread guarantee.
     *
     * @param handle the ticket to commit
     * @return a future carrying success or an explicit failure
     */
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

    /**
     * Drops a hold without applying it.
     *
     * <p>Idempotent on the warehouse side, so a cleanup path may call it unconditionally.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param handle the ticket to release; {@code null} completes immediately
     * @return a future completing once the release finishes
     */
    public CompletableFuture<Void> releaseAsync(ReservationHandle handle) {
        if (handle == null || !usable()) {
            return CompletableFuture.completedFuture(null);
        }
        return EmakiStorageApi.operations().releaseAsync(handle).thenApply(ignored -> (Void) null);
    }

    /**
     * Debits an exact set of amounts in one atomic batch, without a reservation.
     *
     * <p>Used only when the warehouse cannot reserve. The caller is then responsible for depositing the
     * amounts back if a later step fails, which is strictly worse than the reservation path because that
     * deposit can itself be refused for lack of capacity.
     *
     * <p><strong>Thread:</strong> any thread. Requires the owner online. The future carries no
     * completion-thread guarantee.
     *
     * @param playerId the warehouse owner
     * @param amounts  the units to take per identity
     * @return a future carrying the debited materials, or an explicit failure
     */
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
                planned.add(new ConsumedMaterial(source, amount, MaterialChannel.STORAGE));
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

    /**
     * Collects every identity a recipe's requirements accept, de-duplicated in declaration order.
     *
     * @param recipe the recipe to read
     * @return the identity list
     */
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
        List<emaki.jiuwu.craft.storage.api.model.StorageAmount> amounts = batch.applied();
        for (int index = 0; index < order.size() && index < amounts.size(); index++) {
            accepted.put(order.get(index), Math.max(0L, amounts.get(index).applied()));
        }
        return Map.copyOf(accepted);
    }
}
