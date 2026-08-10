package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
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
import emaki.jiuwu.craft.station.material.MergedMaterialChannel;
import emaki.jiuwu.craft.station.material.OutputDelivery;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * Orchestrates submission, settlement, cancellation, and claiming.
 *
 * <h2>Consume on submit</h2>
 * Materials and currency are debited when the entry is created, not when it finishes. The queue entry is
 * therefore the player's receipt: its {@code consumedMaterials} list and its recorded currency charge are the
 * single source of truth for refunds and for reconciling a crash. A failed debit aborts the whole submission;
 * nothing is ever partially taken.
 *
 * <h2>One submission path</h2>
 * There is no longer a separate inventory path and warehouse path. Materials come from a single merged pool
 * spanning the player's inventory and their warehouse, so every submission enters through
 * {@link #submitAsync}. The per-material {@code channel} on each {@code ConsumedMaterial} still records where
 * that particular unit came from, which is what lets a refund put it back in the right place.
 *
 * <p>Every method here touches players, inventories, or GUIs and must run on the target player's owner
 * thread. Warehouse futures complete on unspecified threads, so their continuations hop back through the
 * dispatcher before doing anything Bukkit-visible.
 */
public final class StationCraftService {

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final QueueService queueService;
    private final BackpackChannel backpackChannel;
    private final StorageChannel storageChannel;
    private final MergedMaterialChannel materialChannel;
    private final OutputDelivery outputDelivery;
    private final EconomyManager economyManager;
    private final Supplier<StationRegistry> registrySupplier;
    private final Supplier<Integer> pendingClaimCeiling;
    private final Supplier<Boolean> saveOnSubmit;
    private final Supplier<DebugLogger> debugLoggerSupplier;

    /**
     * Creates the service.
     *
     * @param plugin              the owning plugin, used as the scheduling owner
     * @param dispatcher          CoreLib's execution dispatcher
     * @param queueService        the queue cache
     * @param backpackChannel     the inventory side, used for refunds
     * @param storageChannel      the warehouse side, used for refunds
     * @param materialChannel     the merged material channel used for submissions
     * @param outputDelivery      the output router
     * @param economyManager      CoreLib's economy manager, used for recipe currency costs
     * @param registrySupplier    supplies the current resolved registry, re-read per call so a reload is
     *                            picked up without re-wiring this service
     * @param pendingClaimCeiling supplies the pending-claim ceiling
     * @param saveOnSubmit        supplies whether a successful submission flushes immediately
     * @param debugLoggerSupplier supplies the debug logger; may supply {@code null} when debug is off
     */
    public StationCraftService(Plugin plugin,
            ExecutionDispatcher dispatcher,
            QueueService queueService,
            BackpackChannel backpackChannel,
            StorageChannel storageChannel,
            MergedMaterialChannel materialChannel,
            OutputDelivery outputDelivery,
            EconomyManager economyManager,
            Supplier<StationRegistry> registrySupplier,
            Supplier<Integer> pendingClaimCeiling,
            Supplier<Boolean> saveOnSubmit,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.queueService = queueService;
        this.backpackChannel = backpackChannel;
        this.storageChannel = storageChannel;
        this.materialChannel = materialChannel;
        this.outputDelivery = outputDelivery;
        this.economyManager = economyManager;
        this.registrySupplier = registrySupplier;
        this.pendingClaimCeiling = pendingClaimCeiling;
        this.saveOnSubmit = saveOnSubmit;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    /**
     * Submits one craft, taking its materials from wherever the player has them.
     *
     * <h2>Order of operations, and why</h2>
     * <ol>
     *   <li>validate everything that can be known without touching anything;</li>
     *   <li>fire the cancellable event, so a veto costs nothing;</li>
     *   <li>snapshot both material sources and plan the split;</li>
     *   <li>check the currency balance <em>before</em> debiting materials, because a balance read is free
     *       and rolling materials back is not;</li>
     *   <li>debit materials through the merged channel, which is all-or-nothing across both backends;</li>
     *   <li>charge currency, refunding materials if the charge fails;</li>
     *   <li>enqueue.</li>
     * </ol>
     *
     * <p>Currency is charged after materials rather than before because the material debit is the step most
     * likely to fail, and an economy round trip that has to be reversed is the one this design most wants to
     * avoid. The balance pre-check in step 4 makes a late currency failure rare rather than routine.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param playerId  the crafting player
     * @param stationId the station
     * @param recipeId  the recipe
     * @param batch     how many times to apply the recipe
     * @return a future carrying what the submission produced, or an explicit failure
     */
    public CompletableFuture<EmakiResult<SubmitOutcome>> submitAsync(UUID playerId,
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
        long safeBatch = Math.max(1L, batch);
        MaterialChannel reported = station.storageChannel() && storageChannel.usable()
                ? MaterialChannel.STORAGE
                : MaterialChannel.BACKPACK;
        if (!fireSubmitEvent(player, station, recipe, safeBatch, reported)) {
            return CompletableFuture.completedFuture(EmakiResult.failure(
                    FailureKind.CANCELLED, "station.submit_cancelled"));
        }
        return materialChannel.snapshotAsync(player, station, recipe)
                .thenCompose(availability -> onOwnerThread(player,
                        () -> submitWithSnapshot(player, station, recipe, safeBatch, availability)));
    }

    /**
     * Completes a submission once both material sources are known.
     *
     * <p><strong>Thread:</strong> the crafting player's owner thread.
     *
     * @param player       the crafting player
     * @param station      the station
     * @param recipe       the recipe
     * @param batch        how many times to apply the recipe
     * @param availability the material snapshot to spend against
     * @return a future carrying what the submission produced
     */
    private CompletableFuture<EmakiResult<SubmitOutcome>> submitWithSnapshot(Player player,
            StationDefinition station,
            RecipeDefinition recipe,
            long batch,
            MergedMaterialChannel.Availability availability) {
        MergedMaterialChannel.DebitPlan plan = materialChannel.plan(recipe, batch, availability);
        if (plan == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.insufficient_materials"));
        }
        long charge = recipe.cost().totalFor(batch);
        if (charge > 0L && !affordable(player, recipe.cost().providerId(), charge)) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.insufficient_currency"));
        }
        return materialChannel.debitAsync(player, plan).thenCompose(debited -> {
            if (debited.isFailure()) {
                return CompletableFuture.completedFuture(debited.<SubmitOutcome>retypeFailure());
            }
            List<ConsumedMaterial> materials = debited.orElse(List.of());
            return onOwnerThread(player, () -> {
                if (charge > 0L) {
                    ActionResult removal = economyManager.remove(player, recipe.cost().providerId(),
                            "", (double) charge);
                    if (removal == null || !removal.success()) {
                        // Materials are already gone, so they have to come back before reporting failure.
                        return refundAsync(player, materials, 1.0D).thenCompose(shortfall ->
                                CompletableFuture.completedFuture(
                                        EmakiResult.<SubmitOutcome>rejected(
                                                "station.insufficient_currency")));
                    }
                }
                return enqueue(player, station, recipe, batch,
                        plan.touchesStorage() ? MaterialChannel.STORAGE : MaterialChannel.BACKPACK,
                        materials, recipe.cost().providerId(), charge);
            });
        });
    }

    /**
     * Reads whether a player can pay a currency charge.
     *
     * @param player     the payer
     * @param providerId the economy provider to read
     * @param amount     the amount required
     * @return whether the balance covers the amount
     */
    private boolean affordable(Player player, String providerId, long amount) {
        if (economyManager == null || providerId == null || providerId.isEmpty()) {
            return false;
        }
        return economyManager.getBalance(player, providerId, "") >= (double) amount;
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
                        ConditionContext.of(player))) {
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
            List<ConsumedMaterial> consumed,
            String costProviderId,
            long costAmount) {
        PlayerQueues queues = queueService.cached(player.getUniqueId());
        if (queues == null) {
            return CompletableFuture.completedFuture(EmakiResult.rejected("station.queue_not_loaded"));
        }
        if (recipe.instant()) {
            return settleImmediately(player, station, recipe, batch, channel, consumed, queues,
                    costProviderId, costAmount);
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
                0L,
                costProviderId,
                costAmount);
        int index = queue.add(entry);
        queue.promoteHead(station.progressMode(), System.currentTimeMillis());
        queues.markDirty();
        if (Boolean.TRUE.equals(saveOnSubmit.get())) {
            queueService.saveAsync(player.getUniqueId());
        }
        DebugLogger dl = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (dl != null) {
            dl.log("station", player.getUniqueId(), "station.submit", Map.of(
                    "player", player.getName(),
                    "station", station.id(),
                    "recipe", recipe.id(),
                    "batch", String.valueOf(batch),
                    "queue_index", String.valueOf(index)));
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
            PlayerQueues queues,
            String costProviderId,
            long costAmount) {
        List<PendingOutput> outputs = outputsOf(recipe, batch);
        return outputDelivery.deliverAsync(player, outputs, station.outputRouting())
                .thenCompose(result -> onOwnerThread(player, () -> {
                    if (result.hasPending()) {
                        CraftQueue queue = queues.queue(station.id());
                        QueueEntry entry = new QueueEntry(recipe.id(), batch, channel, 0L, consumed,
                                QueueEntryState.WAITING, 0L, 0L, 0L, costProviderId, costAmount);
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
                    FailureKind.CANCELLED, "station.cancel_vetoed"));
        }
        List<ConsumedMaterial> materials = List.copyOf(entry.consumedMaterials());
        queue.entries().remove(entry);
        queue.promoteHead(station.progressMode(), System.currentTimeMillis());
        queues.markDirty();
        refundCurrency(player, entry, rate);
        return refundAsync(player, materials, rate).thenCompose(shortfall -> onOwnerThread(player, () -> {
            queueService.saveAsync(player.getUniqueId());
            return CompletableFuture.completedFuture(shortfall
                    ? EmakiResult.<Unit>partial(Unit.INSTANCE, "station.refund_partial")
                    : EmakiResult.ok());
        }));
    }

    /**
     * Returns part of a cancelled entry's currency charge.
     *
     * <p>The same rate applies to currency as to materials, so a station configured to refund half gives back
     * half the money too. Refunding at a different rate would need a second configuration key whose only
     * purpose is to disagree with the first.
     *
     * <p>The charge is read from the entry rather than recomputed from the recipe: the recipe may have been
     * re-priced or removed since the submission, and the player is owed what they actually paid.
     *
     * <p><strong>Thread:</strong> the owning player's owner thread.
     *
     * @param player the player being refunded
     * @param entry  the cancelled entry
     * @param rate   the station's refund rate
     */
    private void refundCurrency(Player player, QueueEntry entry, double rate) {
        if (economyManager == null || !entry.charged()) {
            return;
        }
        long refundable = (long) Math.floor(entry.costAmount() * Math.clamp(rate, 0.0D, 1.0D));
        if (refundable <= 0L) {
            return;
        }
        economyManager.add(player, entry.costProviderId(), "", (double) refundable);
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
            Supplier<CompletableFuture<T>> work) {
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
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}
