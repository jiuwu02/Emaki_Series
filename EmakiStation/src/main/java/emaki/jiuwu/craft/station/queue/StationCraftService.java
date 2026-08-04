package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.station.api.event.StationCraftCancelEvent;
import emaki.jiuwu.craft.station.api.event.StationCraftCompletedEvent;
import emaki.jiuwu.craft.station.api.event.StationCraftSubmitEvent;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.api.model.SubmitOutcome;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.material.BackpackChannel;
import emaki.jiuwu.craft.station.material.OutputDelivery;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * Orchestrates submission, settlement, cancellation, and claiming.
 *
 * <h2>Consume on submit</h2>
 * Materials are debited when the entry is created, not when it finishes. The queue entry is therefore the
 * player's receipt, and its {@code consumedMaterials} list is the single source of truth for refunds and for
 * reconciling a crash. A failed debit aborts the whole submission; nothing is ever partially taken.
 *
 * <p>Every method here touches players, inventories, or GUIs and must run on the target player's owner
 * thread. Warehouse futures complete on unspecified threads, so their continuations hop back through the
 * dispatcher before doing anything Bukkit-visible.
 */
public final class StationCraftService {

    private final org.bukkit.plugin.Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final QueueService queueService;
    private final BackpackChannel backpackChannel;
    private final StorageChannel storageChannel;
    private final OutputDelivery outputDelivery;
    private final java.util.function.Supplier<StationRegistry> registrySupplier;
    private final java.util.function.Supplier<Integer> pendingClaimCeiling;
    private final java.util.function.Supplier<Boolean> saveOnSubmit;

    /**
     * Creates the service.
     *
     * @param plugin              the owning plugin, used as the scheduling owner
     * @param dispatcher          CoreLib's execution dispatcher
     * @param queueService        the queue cache
     * @param backpackChannel     the inventory channel
     * @param storageChannel      the warehouse channel
     * @param outputDelivery      the output router
     * @param registrySupplier    supplies the current resolved registry, re-read per call so a reload is
     *                            picked up without re-wiring this service
     * @param pendingClaimCeiling supplies the pending-claim ceiling
     * @param saveOnSubmit        supplies whether a successful submission flushes immediately
     */
    public StationCraftService(org.bukkit.plugin.Plugin plugin,
            ExecutionDispatcher dispatcher,
            QueueService queueService,
            BackpackChannel backpackChannel,
            StorageChannel storageChannel,
            OutputDelivery outputDelivery,
            java.util.function.Supplier<StationRegistry> registrySupplier,
            java.util.function.Supplier<Integer> pendingClaimCeiling,
            java.util.function.Supplier<Boolean> saveOnSubmit) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.queueService = queueService;
        this.backpackChannel = backpackChannel;
        this.storageChannel = storageChannel;
        this.outputDelivery = outputDelivery;
        this.registrySupplier = registrySupplier;
        this.pendingClaimCeiling = pendingClaimCeiling;
        this.saveOnSubmit = saveOnSubmit;
    }

    /**
     * Submits one craft from the warehouse channel.
     *
     * <p>The inventory channel is not reachable here because its materials live in a GUI session's input
     * slots, which only the GUI layer owns; that path calls {@link #submitFromInputs} instead.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param playerId  the crafting player
     * @param stationId the station
     * @param recipeId  the recipe
     * @param batch     how many times to apply the recipe
     * @return a future carrying what the submission produced, or an explicit failure
     */
    public CompletableFuture<EmakiResult<SubmitOutcome>> submitFromStorageAsync(UUID playerId,
            String stationId,
            String recipeId,
            long batch) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(EmakiResult.targetOffline());
        }
        StationRegistry registry = registrySupplier.get();
        StationDefinition station = registry.station(stationId);
        RecipeDefinition recipe = registry.recipe(recipeId);
        EmakiResult<Unit> gate = validate(player, registry, station, recipe, batch);
        if (gate.isFailure()) {
            return CompletableFuture.completedFuture(gate.retypeFailure());
        }
        if (!station.storageChannel() || !storageChannel.usable()) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.storage_unavailable"));
        }
        long safeBatch = Math.max(1L, batch);
        if (!fireSubmitEvent(player, station, recipe, safeBatch, MaterialChannel.STORAGE)) {
            return CompletableFuture.completedFuture(EmakiResult.failure(
                    emaki.jiuwu.craft.corelib.api.contract.FailureKind.CANCELLED, "station.submit_cancelled"));
        }
        return storageChannel.consumeAsync(playerId, recipe, safeBatch).thenCompose(consumed -> {
            if (consumed.isFailure()) {
                return CompletableFuture.completedFuture(consumed.<SubmitOutcome>retypeFailure());
            }
            List<ConsumedMaterial> materials = consumed.orElse(List.of());
            return onOwnerThread(player, () -> enqueue(player, station, recipe, safeBatch,
                    MaterialChannel.STORAGE, materials));
        });
    }

    /**
     * Submits one craft using materials already consumed from a GUI session's input slots.
     *
     * <p>Consumption has already happened by the time this is called, which is why the caller passes the
     * receipt in: the GUI owns the input slots and can apply plan/rollback synchronously, so splitting the
     * debit from the enqueue keeps both halves on the same thread with no window between them.
     *
     * <p><strong>Thread:</strong> the crafting player's owner thread.
     *
     * @param player    the crafting player
     * @param station   the station
     * @param recipe    the recipe
     * @param batch     how many times the recipe was applied
     * @param consumed  the already-debited materials
     * @return a future carrying what the submission produced
     */
    public CompletableFuture<EmakiResult<SubmitOutcome>> submitFromInputs(Player player,
            StationDefinition station,
            RecipeDefinition recipe,
            long batch,
            List<ConsumedMaterial> consumed) {
        return enqueue(player, station, recipe, Math.max(1L, batch), MaterialChannel.BACKPACK, consumed);
    }

    /**
     * Validates everything that must hold before any material is touched.
     *
     * @param player   the crafting player
     * @param registry the resolved registry
     * @param station  the station, possibly {@code null}
     * @param recipe   the recipe, possibly {@code null}
     * @param batch    the requested batch
     * @return success when the submission may proceed, otherwise the reason it may not
     */
    public EmakiResult<Unit> validate(Player player,
            StationRegistry registry,
            StationDefinition station,
            RecipeDefinition recipe,
            long batch) {
        if (station == null) {
            return EmakiResult.notFound("station.unknown_station");
        }
        if (recipe == null) {
            return EmakiResult.notFound("station.unknown_recipe");
        }
        if (batch <= 0L) {
            return EmakiResult.invalidInput("station.bad_batch");
        }
        if (!registry.recipeIdsOf(station.id()).contains(recipe.id())) {
            return EmakiResult.rejected("station.recipe_not_at_station");
        }
        if (recipe.hasPermission() && !player.hasPermission(recipe.permission())) {
            return EmakiResult.rejected("station.recipe_no_permission");
        }
        if (recipe.condition().configured()
                && !ConditionEvaluator.evaluate(recipe.condition(),
                        text -> resolvePlaceholders(player, text),
                        emaki.jiuwu.craft.corelib.api.condition.ConditionContext.of(player))) {
            return EmakiResult.rejected("station.recipe_condition_failed");
        }
        PlayerQueues queues = queueService.cached(player.getUniqueId());
        if (queues == null) {
            return EmakiResult.rejected("station.queue_not_loaded");
        }
        if (queues.totalPendingClaims() >= pendingClaimCeiling.get()) {
            return EmakiResult.rejected("station.pending_claim_full");
        }
        if (!recipe.instant()) {
            CraftQueue queue = queues.existingQueue(station.id());
            int occupied = queue == null ? 0 : queue.occupiedLength();
            if (occupied >= QueueCapacity.effectiveLength(player, station)) {
                return EmakiResult.rejected("station.queue_full");
            }
        }
        return EmakiResult.ok();
    }

    /**
     * Fires the pre-consumption event.
     *
     * @param player  the crafting player
     * @param station the station
     * @param recipe  the recipe
     * @param batch   the batch
     * @param channel the material channel
     * @return whether the submission may proceed
     */
    public boolean fireSubmitEvent(Player player,
            StationDefinition station,
            RecipeDefinition recipe,
            long batch,
            MaterialChannel channel) {
        StationCraftSubmitEvent event =
                new StationCraftSubmitEvent(player, station.id(), recipe.id(), batch, channel);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private CompletableFuture<EmakiResult<SubmitOutcome>> enqueue(Player player,
            StationDefinition station,
            RecipeDefinition recipe,
            long batch,
            MaterialChannel channel,
            List<ConsumedMaterial> consumed) {
        PlayerQueues queues = queueService.cached(player.getUniqueId());
        if (queues == null) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.queue_not_loaded"));
        }
        if (recipe.instant()) {
            return settleImmediately(player, station, recipe, batch, channel, consumed, queues);
        }
        CraftQueue queue = queues.queue(station.id());
        QueueEntry entry = new QueueEntry(recipe.id(),
                batch,
                channel,
                recipe.effectiveDurationMillis(station.queueSettings().speedMultiplier()),
                consumed,
                QueueEntryState.WAITING,
                0L,
                0L,
                0L);
        int index = queue.add(entry);
        queue.promoteHead(station.progressMode(), System.currentTimeMillis());
        queues.markDirty();
        if (Boolean.TRUE.equals(saveOnSubmit.get())) {
            queueService.saveAsync(player.getUniqueId());
        }
        return CompletableFuture.completedFuture(EmakiResult.success(
                new SubmitOutcome(recipe.id(), batch, true, index, consumed, List.of())));
    }

    private CompletableFuture<EmakiResult<SubmitOutcome>> settleImmediately(Player player,
            StationDefinition station,
            RecipeDefinition recipe,
            long batch,
            MaterialChannel channel,
            List<ConsumedMaterial> consumed,
            PlayerQueues queues) {
        List<PendingOutput> outputs = outputsOf(recipe, batch);
        return outputDelivery.deliverAsync(player, outputs, station.outputRouting())
                .thenCompose(result -> onOwnerThread(player, () -> {
                    if (result.hasPending()) {
                        CraftQueue queue = queues.queue(station.id());
                        QueueEntry entry = new QueueEntry(recipe.id(), batch, channel, 0L, consumed,
                                QueueEntryState.WAITING, 0L, 0L, 0L);
                        entry.markPendingClaim(result.pending());
                        queue.add(entry);
                        queues.markDirty();
                        queueService.saveAsync(player.getUniqueId());
                    }
                    fireCompleted(player, station, recipe, batch, result.delivered(), result.pending());
                    return CompletableFuture.completedFuture(EmakiResult.success(new SubmitOutcome(
                            recipe.id(), batch, false, -1, consumed, result.pending())));
                }));
    }

    /**
     * Settles one due entry.
     *
     * <p><strong>Thread:</strong> the owning player's owner thread.
     *
     * @param player  the owning player
     * @param station the station
     * @param queue   the queue holding the entry
     * @param entry   the due entry
     * @return a future completing once delivery finishes
     */
    public CompletableFuture<Void> settleAsync(Player player,
            StationDefinition station,
            CraftQueue queue,
            QueueEntry entry) {
        StationRegistry registry = registrySupplier.get();
        RecipeDefinition recipe = registry.recipe(entry.recipeId());
        if (recipe == null) {
            // The recipe was removed from configuration while the entry was queued. Keep the entry as a
            // pending claim carrying nothing rather than deleting the player's receipt silently.
            entry.markPendingClaim(List.of());
            return CompletableFuture.completedFuture(null);
        }
        List<PendingOutput> outputs = outputsOf(recipe, entry.batch());
        return outputDelivery.deliverAsync(player, outputs, station.outputRouting())
                .thenCompose(result -> onOwnerThread(player, () -> {
                    PlayerQueues queues = queueService.cached(player.getUniqueId());
                    if (result.hasPending()) {
                        entry.markPendingClaim(result.pending());
                    } else {
                        queue.entries().remove(entry);
                    }
                    if (queues != null) {
                        queues.markDirty();
                    }
                    queue.promoteHead(station.progressMode(), System.currentTimeMillis());
                    fireCompleted(player, station, recipe, entry.batch(), result.delivered(), result.pending());
                    return CompletableFuture.<Void>completedFuture(null);
                }));
    }

    /**
     * Cancels one entry and refunds its materials at the station's configured rate.
     *
     * <p>Refunds return to the channel each material came from, not to the station's output routing: the
     * player is getting their input back, not receiving a product.
     *
     * <p>Progress is not prorated. Under serial single-line queues only the head has any progress at all, so
     * per-entry progress weighting would add accounting for a case that barely exists. This is a deliberate
     * simplification and the configuration comments say so.
     *
     * <p><strong>Thread:</strong> the owning player's owner thread.
     *
     * @param player  the cancelling player
     * @param station the station
     * @param index   the queue position to cancel
     * @return a future carrying success or an explicit failure
     */
    public CompletableFuture<EmakiResult<Unit>> cancelAsync(Player player,
            StationDefinition station,
            int index) {
        PlayerQueues queues = queueService.cached(player.getUniqueId());
        if (queues == null) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.queue_not_loaded"));
        }
        CraftQueue queue = queues.existingQueue(station.id());
        QueueEntry entry = queue == null ? null : queue.at(index);
        if (entry == null) {
            return CompletableFuture.completedFuture(EmakiResult.notFound("station.queue_entry_missing"));
        }
        if (entry.state() == QueueEntryState.PENDING_CLAIM) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.cancel_pending_claim"));
        }
        double rate = station.queueSettings().cancelRefundRate();
        StationCraftCancelEvent event = new StationCraftCancelEvent(player, station.id(), entry.recipeId(),
                index, List.copyOf(entry.consumedMaterials()), rate);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(EmakiResult.failure(
                    emaki.jiuwu.craft.corelib.api.contract.FailureKind.CANCELLED, "station.cancel_vetoed"));
        }
        List<ConsumedMaterial> materials = List.copyOf(entry.consumedMaterials());
        queue.entries().remove(entry);
        queue.promoteHead(station.progressMode(), System.currentTimeMillis());
        queues.markDirty();
        return refundAsync(player, materials, rate).thenCompose(shortfall -> onOwnerThread(player, () -> {
            queueService.saveAsync(player.getUniqueId());
            return CompletableFuture.completedFuture(shortfall
                    ? EmakiResult.<Unit>partial(Unit.INSTANCE, "station.refund_partial")
                    : EmakiResult.ok());
        }));
    }

    /**
     * Claims every deliverable pending output a player owns.
     *
     * <p><strong>Thread:</strong> the claiming player's owner thread.
     *
     * @param player the claiming player
     * @return a future carrying how many entries were cleared
     */
    public CompletableFuture<EmakiResult<Integer>> claimAsync(Player player) {
        PlayerQueues queues = queueService.cached(player.getUniqueId());
        if (queues == null) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.queue_not_loaded"));
        }
        List<QueueService.ClaimableEntry> claimable =
                queueService.claimable(registrySupplier.get(), queues);
        if (claimable.isEmpty()) {
            return CompletableFuture.completedFuture(EmakiResult.success(0));
        }
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (QueueService.ClaimableEntry claim : claimable) {
            chain = chain.thenCompose(cleared -> outputDelivery
                    .claimAsync(player, claim.outputs(), claim.station().outputRouting())
                    .thenCompose(result -> onOwnerThread(player, () -> {
                        if (result.hasPending()) {
                            claim.entry().markPendingClaim(result.pending());
                            return CompletableFuture.completedFuture(cleared);
                        }
                        claim.entry().clearPendingOutputs();
                        claim.queue().entries().remove(claim.entry());
                        return CompletableFuture.completedFuture(cleared + 1);
                    })));
        }
        return chain.thenCompose(cleared -> onOwnerThread(player, () -> {
            queues.markDirty();
            queueService.saveAsync(player.getUniqueId());
            return CompletableFuture.completedFuture(EmakiResult.success(cleared));
        }));
    }

    private CompletableFuture<Boolean> refundAsync(Player player,
            List<ConsumedMaterial> materials,
            double rate) {
        Map<ItemSourceRef, Long> toStorage = new LinkedHashMap<>();
        List<ConsumedMaterial> toBackpack = new ArrayList<>();
        for (ConsumedMaterial material : materials) {
            long refundable = (long) Math.floor(material.amount() * Math.clamp(rate, 0.0D, 1.0D));
            if (refundable <= 0L) {
                continue;
            }
            if (material.channel() == MaterialChannel.STORAGE) {
                toStorage.merge(material.source(), refundable, Long::sum);
            } else {
                toBackpack.add(new ConsumedMaterial(material.source(), refundable, material.channel()));
            }
        }
        boolean[] shortfall = {false};
        for (ConsumedMaterial material : toBackpack) {
            long delivered = backpackChannel.refund(player, material.source(), material.amount());
            if (delivered < material.amount()) {
                shortfall[0] = true;
            }
        }
        if (toStorage.isEmpty()) {
            return CompletableFuture.completedFuture(shortfall[0]);
        }
        if (!storageChannel.usable()) {
            // The warehouse is gone, so returning materials there is impossible. Hand them to the player
            // instead of dropping them from the ledger entirely.
            for (Map.Entry<ItemSourceRef, Long> entry : toStorage.entrySet()) {
                long delivered = backpackChannel.refund(player, entry.getKey(), entry.getValue());
                if (delivered < entry.getValue()) {
                    shortfall[0] = true;
                }
            }
            return CompletableFuture.completedFuture(shortfall[0]);
        }
        Map<ItemSourceRef, Long> requested = Map.copyOf(toStorage);
        return storageChannel.depositAsync(player.getUniqueId(), requested).thenApply(accepted -> {
            for (Map.Entry<ItemSourceRef, Long> entry : requested.entrySet()) {
                if (accepted.getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                    return true;
                }
            }
            return shortfall[0];
        });
    }

    private void fireCompleted(Player player,
            StationDefinition station,
            RecipeDefinition recipe,
            long batch,
            List<PendingOutput> delivered,
            List<PendingOutput> pending) {
        Bukkit.getPluginManager().callEvent(new StationCraftCompletedEvent(player, station.id(),
                recipe.id(), batch, delivered, pending));
    }

    private static List<PendingOutput> outputsOf(RecipeDefinition recipe, long batch) {
        List<PendingOutput> outputs = new ArrayList<>(recipe.outputs().size());
        recipe.outputs().forEach(output -> outputs.add(output.toPending(batch)));
        return outputs;
    }

    /**
     * Runs a continuation on the target player's owner thread.
     *
     * <p>Storage futures complete on unspecified threads, so anything that touches the player, their
     * inventory, or their GUI has to be dispatched rather than run inline.
     *
     * @param player the owner
     * @param work   the continuation
     * @param <T>    the continuation's result type
     * @return a future carrying the continuation's result
     */
    private <T> CompletableFuture<T> onOwnerThread(Player player,
            java.util.function.Supplier<CompletableFuture<T>> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        dispatcher.runEntity(plugin, player, () -> {
            try {
                work.get().whenComplete((value, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                    } else {
                        future.complete(value);
                    }
                });
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        }, () -> future.completeExceptionally(
                new IllegalStateException("player retired before station work ran")));
        return future;
    }

    private String resolvePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}
