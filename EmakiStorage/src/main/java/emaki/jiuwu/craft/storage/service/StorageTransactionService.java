package emaki.jiuwu.craft.storage.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.storage.api.event.StorageBatchEvent;
import emaki.jiuwu.craft.storage.api.event.StorageDepositEvent;
import emaki.jiuwu.craft.storage.api.event.StorageWithdrawEvent;
import emaki.jiuwu.craft.storage.api.model.StorageBatchOp;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.model.StorageResult;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.model.StorageReservation;

/**
 * The single implementation of deposit and withdrawal.
 *
 * <p>Both GUI deposit paths — clicking a display slot with a loaded cursor, and the fixed
 * {@code deposit_slot} — funnel through {@link #depositFromInventory} / {@link #depositCursor}
 * into one transaction body. Capacity checks, the deposit filter, the unique-item rule, event
 * publication and log emission exist exactly once; duplicating them would guarantee behavioural
 * drift and one of the two copies eventually skipping a check.
 *
 * <p>Everything here runs on the owning entity thread. That is not laziness about async: a deposit
 * is an atomic trade between the player's inventory and the entry table, the inventory can only be
 * touched on its owner thread, so the whole trade belongs there. Making the entry table async
 * would break that atomicity and open an item-duplication window. Serialisation and file IO, which
 * genuinely can be async, happen later on the file lane and never inside this path.
 */
public final class StorageTransactionService {

    private final ItemSourceService itemSourceService;
    private final StorageCapacityService capacityService;
    private final StorageTextIndexer textIndexer;
    private final StorageOperationLog operationLog;
    private final Supplier<DebugLogger> debugLoggerSupplier;

    private volatile AppConfig config;

    public StorageTransactionService(ItemSourceService itemSourceService,
            StorageCapacityService capacityService,
            StorageTextIndexer textIndexer,
            StorageOperationLog operationLog,
            AppConfig config,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.itemSourceService = itemSourceService;
        this.capacityService = capacityService;
        this.textIndexer = textIndexer;
        this.operationLog = operationLog;
        this.config = config;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    public void reconfigure(AppConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    /**
     * Deposits from the player's cursor.
     *
     * @param storage  the target storage
     * @param player   the acting player, also the inventory owner
     * @param capacity the freshly computed capacity breakdown
     * @param cursor   the cursor contents
     * @param amount   how many units to take from the cursor
     * @param source   the originating surface
     * @return the outcome plus the cursor remainder the caller must write back
     */
    public CursorDepositResult depositCursor(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            ItemStack cursor,
            int amount,
            StorageOperationSource source) {
        if (cursor == null || cursor.getType().isAir() || amount <= 0) {
            return new CursorDepositResult(StorageResult.failed(amount, "empty_cursor"), cursor);
        }
        int requested = Math.min(amount, cursor.getAmount());
        StorageResult result = applyDeposit(storage, player, capacity, cursor, requested, source);
        if (!result.applied()) {
            return new CursorDepositResult(result, cursor);
        }
        ItemStack remainder = cursor.clone();
        int left = cursor.getAmount() - (int) Math.min(Integer.MAX_VALUE, result.appliedAmount());
        if (left <= 0) {
            remainder = null;
        } else {
            remainder.setAmount(left);
        }
        return new CursorDepositResult(result, remainder);
    }

    /**
     * Outcome of a cursor deposit.
     *
     * @param result          the transaction outcome
     * @param remainingCursor what the cursor must be set to afterwards, {@code null} to clear it
     */
    public record CursorDepositResult(StorageResult result, ItemStack remainingCursor) {
    }

    /**
     * Deposits from a specific player inventory slot, used by shift-click redirection.
     *
     * @param storage  the target storage
     * @param player   the acting player
     * @param capacity the freshly computed capacity breakdown
     * @param slot     the player inventory slot index
     * @param source   the originating surface
     * @return the transaction outcome
     */
    public StorageResult depositFromInventory(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            int slot,
            StorageOperationSource source) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType().isAir()) {
            return StorageResult.failed(0L, "empty_slot");
        }
        int requested = stack.getAmount();
        StorageKey key = StorageKey.of(stack);
        PreCheck preCheck = preCheck(storage, key, capacity);
        if (preCheck.rejection() != null) {
            return StorageResult.failed(requested, preCheck.rejection());
        }
        long acceptable = Math.min(requested, preCheck.room());
        if (acceptable <= 0L) {
            return StorageResult.failed(requested, "slot_full");
        }
        if (!fireDepositEvent(storage, key, acceptable, source)) {
            return StorageResult.cancelled(requested);
        }
        InventoryItemUtil.RemovalPlan plan = planExactRemoval(inventory, key, acceptable);
        if (plan.removedAmount() <= 0L || !InventoryItemUtil.applyRemoval(inventory, plan)) {
            return StorageResult.failed(requested, "inventory_conflict");
        }
        return commitDeposit(storage, player, inventory, plan, key, preCheck, requested, source);
    }

    /**
     * Deposits every matching stack from the player's main inventory, used by the bulk button.
     *
     * <p>All 36 slots are processed inside one owner-thread pass. Doing it in chunks across ticks
     * would let the inventory change mid-operation; a table lookup only costs
     * {@code hashCode}/{@code equals} and involves no serialisation, so one pass is affordable.
     *
     * @param storage  the target storage
     * @param player   the acting player
     * @param capacity the capacity breakdown, recomputed internally as slots fill
     * @param source   the originating surface
     * @return how many units were stored in total
     */
    public long depositAll(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            StorageOperationSource source) {
        PlayerInventory inventory = player.getInventory();
        long stored = 0L;
        StorageCapacity current = capacity;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            StorageResult result = depositFromInventory(storage, player, current, slot, source);
            if (result.applied()) {
                stored += result.appliedAmount();
                current = capacityService.capacityOf(storage, player, current.slotsPerPage());
            }
        }
        return stored;
    }

    /**
     * Adds items without touching any inventory, used by the API, actions and admin commands.
     *
     * @param storage  the target storage
     * @param player   the online player, or {@code null} for offline targets
     * @param capacity the capacity breakdown
     * @param template the item to store
     * @param amount   how many units to add
     * @param source   the originating surface
     * @return the transaction outcome
     */
    public StorageResult depositDirect(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            ItemStack template,
            long amount,
            StorageOperationSource source) {
        if (template == null || template.getType().isAir() || amount <= 0L) {
            return StorageResult.failed(Math.max(0L, amount), "invalid_item");
        }
        StorageKey key = StorageKey.of(template);
        PreCheck preCheck = preCheck(storage, key, capacity);
        if (preCheck.rejection() != null) {
            return StorageResult.failed(amount, preCheck.rejection());
        }
        long acceptable = Math.min(amount, preCheck.room());
        if (acceptable <= 0L) {
            return StorageResult.failed(amount, "slot_full");
        }
        if (!fireDepositEvent(storage, key, acceptable, source)) {
            return StorageResult.cancelled(amount);
        }
        long applied = credit(storage, key, preCheck, acceptable);
        if (applied <= 0L) {
            return StorageResult.failed(amount, "slot_full");
        }
        storage.markDirty();
        logDeposit(storage, key, applied, source);
        return applied >= amount
                ? StorageResult.success(amount)
                : StorageResult.partial(amount, applied, "slot_full");
    }

    /**
     * The shared deposit body used by both GUI paths.
     */
    private StorageResult applyDeposit(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            ItemStack cursor,
            int requested,
            StorageOperationSource source) {
        StorageKey key = StorageKey.of(cursor);
        PreCheck preCheck = preCheck(storage, key, capacity);
        if (preCheck.rejection() != null) {
            return StorageResult.failed(requested, preCheck.rejection());
        }
        long acceptable = Math.min(requested, preCheck.room());
        if (acceptable <= 0L) {
            return StorageResult.failed(requested, "slot_full");
        }
        if (!fireDepositEvent(storage, key, acceptable, source)) {
            return StorageResult.cancelled(requested);
        }
        long applied = credit(storage, key, preCheck, acceptable);
        if (applied <= 0L) {
            return StorageResult.failed(requested, "slot_full");
        }
        storage.markDirty();
        logDeposit(storage, key, applied, source);
        return applied >= requested
                ? StorageResult.success(requested)
                : StorageResult.partial(requested, applied, "slot_full");
    }

    private StorageResult commitDeposit(PlayerStorage storage,
            Player player,
            PlayerInventory inventory,
            InventoryItemUtil.RemovalPlan plan,
            StorageKey key,
            PreCheck preCheck,
            long requested,
            StorageOperationSource source) {
        long applied;
        try {
            applied = credit(storage, key, preCheck, plan.removedAmount());
        } catch (RuntimeException failure) {
            InventoryItemUtil.rollbackRemoval(inventory, plan);
            throw failure;
        }
        if (applied < plan.removedAmount()) {
            // The table accepted less than was taken: give the difference back rather than void it.
            long surplus = plan.removedAmount() - applied;
            ItemStack refund = key.toItemStack((int) Math.min(Integer.MAX_VALUE, surplus));
            InventoryItemUtil.addOrDrop(player, refund);
        }
        if (applied <= 0L) {
            return StorageResult.failed(requested, "slot_full");
        }
        storage.markDirty();
        logDeposit(storage, key, applied, source);
        return applied >= requested
                ? StorageResult.success(requested)
                : StorageResult.partial(requested, applied, "slot_full");
    }

    /**
     * Withdraws items into a player's inventory.
     *
     * <p>Debit happens before the hand-out. The reverse order would mean a successful hand-out
     * followed by a failed debit — item duplication. In this order the worst case is the player
     * receiving less than asked, and that surplus is credited straight back.
     *
     * @param storage  the source storage
     * @param player   the receiving player
     * @param key      the entry to withdraw from
     * @param amount   how many units to withdraw; larger than stock simply takes everything
     * @param source   the originating surface
     * @return the transaction outcome
     */
    public StorageResult withdraw(PlayerStorage storage,
            Player player,
            StorageKey key,
            long amount,
            StorageOperationSource source) {
        StorageEntry entry = storage.entry(key);
        if (entry == null || entry.empty()) {
            return StorageResult.failed(amount, "entry_missing");
        }
        long requested = Math.max(0L, amount);
        if (requested <= 0L) {
            return StorageResult.failed(0L, "invalid_amount");
        }
        long available = Math.min(requested, entry.amount());
        if (!fireWithdrawEvent(storage, key, available, entry.amount(), source)) {
            return StorageResult.cancelled(requested);
        }
        long debited = entry.remove(available);
        if (debited <= 0L) {
            return StorageResult.failed(requested, "entry_missing");
        }

        // Data-layer item, never the GUI projection: the rendered item carries percentage lore and
        // an overridden max_stack_size, which would be baked into the player's item permanently.
        long leftover = handOut(player, key, debited);
        if (leftover > 0L) {
            if (config.behavior().overflowOnWithdraw() == AppConfig.WithdrawOverflow.RETURN) {
                entry.add(leftover, Long.MAX_VALUE);
                debited -= leftover;
            } else {
                leftover = 0L;
            }
        }
        if (entry.empty()) {
            storage.remove(key);
        }
        if (debited <= 0L) {
            return StorageResult.failed(requested, "inventory_full");
        }
        storage.markDirty();
        StorageEntry remaining = storage.entry(key);
        operationLog.record(StorageLogEntry.of(storage.playerId(), StorageOperationType.WITHDRAW,
                textIndexer.identifierOf(key), -debited,
                remaining == null ? 0L : remaining.amount(), source, uniqueNote(key)));
        return debited >= requested
                ? StorageResult.success(requested)
                : StorageResult.partial(requested, debited, "inventory_full");
    }

    /**
     * Splits the amount into vanilla-sized stacks and hands them to the player.
     *
     * @return how many units the inventory refused
     */
    private long handOut(Player player, StorageKey key, long amount) {
        int stackSize = key.vanillaMaxStackSize();
        long remaining = amount;
        long refused = 0L;
        while (remaining > 0L) {
            int give = (int) Math.min(stackSize, remaining);
            ItemStack stack = key.toItemStack(give);
            Map<Integer, ItemStack> leftover = InventoryItemUtil.addOrDrop(player, stack);
            remaining -= give;
            if (leftover != null && !leftover.isEmpty()) {
                for (ItemStack rejected : leftover.values()) {
                    if (rejected != null) {
                        refused += rejected.getAmount();
                    }
                }
            }
        }
        return refused;
    }

    /**
     * Per-op outcome of one batch, plus where and why it stopped.
     *
     * @param requested   unsigned requested amount per op, positionally aligned with the request
     * @param applied     unsigned applied amount per op, positionally aligned with the request
     * @param failedIndex the op that could not be applied in full, or {@code -1}
     * @param reasonKey   reason key for {@code failedIndex}, or an empty string
     * @param cancelled   whether a listener cancelled the batch before anything moved
     */
    public record BatchOutcome(List<Long> requested, List<Long> applied, int failedIndex, String reasonKey,
            boolean cancelled) {

        /** {@return whether some op could not be applied in full} */
        public boolean failed() {
            return failedIndex >= 0;
        }
    }

    /**
     * Applies a batch of signed increments in one owner-thread pass, never touching any inventory.
     *
     * <p>Three phases, in this order, and the order is the whole point:</p>
     * <ol>
     *   <li><strong>pre-check</strong> on a projection of the entry table, so an under-stocked or
     *       over-capacity batch is rejected before a single unit moves;</li>
     *   <li><strong>one event</strong> for the batch, cancellable, still before any mutation;</li>
     *   <li><strong>apply</strong> in list order, recording enough to undo.</li>
     * </ol>
     *
     * <p>Under {@code allOrNothing} any op that cannot be applied in full triggers an exact undo of
     * the ops already applied. The undo is exact because nothing outside the entry table is touched:
     * amounts are pure arithmetic, and emptied entries are deliberately <em>not</em> pruned until the
     * batch has committed. Pruning mid-batch would shift every later slot index, and re-appending on
     * undo would put the entry back at the tail instead of its original position.
     *
     * @param storage      the target storage
     * @param player       the online storage owner, used only to recompute capacity
     * @param capacity     the capacity breakdown captured before the batch
     * @param ops          the increments, in application order
     * @param allOrNothing whether a single unapplicable op aborts and undoes the whole batch
     * @param source       the originating surface
     * @return the per-op outcome
     */
    public BatchOutcome applyBatch(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            List<StorageBatchOp> ops,
            boolean allOrNothing,
            StorageOperationSource source) {
        int size = ops.size();
        List<StorageKey> keys = new ArrayList<>(size);
        List<Long> requested = new ArrayList<>(size);
        for (StorageBatchOp op : ops) {
            keys.add(StorageKey.of(op.template()));
            requested.add(op.magnitude());
        }
        // Expired holds must stop counting against available stock before the pre-check reads it,
        // or a caller that never committed would keep the units frozen for the whole session.
        storage.pruneExpiredReservations(System.currentTimeMillis());

        BatchRejection rejection = preCheckBatch(storage, capacity, ops, keys);
        if (rejection != null && allOrNothing) {
            return new BatchOutcome(requested, zeros(size), rejection.index(), rejection.reasonKey(), false);
        }
        if (!fireBatchEvent(storage, ops, allOrNothing, source)) {
            return new BatchOutcome(requested, zeros(size), -1, "batch_cancelled", true);
        }

        Set<StorageKey> preexisting = new HashSet<>();
        for (StorageKey key : keys) {
            if (storage.entry(key) != null) {
                preexisting.add(key);
            }
        }

        List<Long> applied = new ArrayList<>(size);
        List<AppliedOp> undoLog = new ArrayList<>(size);
        StorageCapacity current = capacity;
        int firstFailIndex = -1;
        String firstFailReason = "";
        for (int index = 0; index < size; index++) {
            StorageBatchOp op = ops.get(index);
            StorageKey key = keys.get(index);
            long magnitude = op.magnitude();
            long moved;
            String failure;
            if (op.delta() == 0L) {
                moved = 0L;
                failure = "invalid_delta";
            } else if (op.withdrawal()) {
                moved = debitForBatch(storage, key, magnitude);
                failure = moved < magnitude ? "insufficient_stock" : null;
            } else {
                PreCheck preCheck = preCheck(storage, key, current);
                if (preCheck.rejection() != null) {
                    moved = 0L;
                    failure = preCheck.rejection();
                } else {
                    long acceptable = Math.min(magnitude, preCheck.room());
                    moved = acceptable <= 0L ? 0L : credit(storage, key, preCheck, acceptable);
                    failure = moved < magnitude ? "slot_full" : null;
                }
            }
            if (failure != null && allOrNothing) {
                if (moved > 0L) {
                    undoLog.add(new AppliedOp(key, op.delta() < 0L ? -moved : moved));
                }
                undo(storage, undoLog, preexisting);
                return new BatchOutcome(requested, zeros(size), index, failure, false);
            }
            if (failure != null && firstFailIndex < 0) {
                firstFailIndex = index;
                firstFailReason = failure;
            }
            applied.add(moved);
            if (moved > 0L) {
                undoLog.add(new AppliedOp(key, op.delta() < 0L ? -moved : moved));
                current = capacityService.capacityOf(storage, player, capacity.slotsPerPage());
            }
        }

        if (undoLog.isEmpty()) {
            return new BatchOutcome(requested, applied, firstFailIndex, firstFailReason, false);
        }
        storage.pruneEmpty();
        storage.markDirty();
        for (int index = 0; index < size; index++) {
            long moved = applied.get(index);
            if (moved <= 0L) {
                continue;
            }
            StorageKey key = keys.get(index);
            if (ops.get(index).withdrawal()) {
                logWithdraw(storage, key, moved, source);
            } else {
                logDeposit(storage, key, moved, source);
            }
        }
        return new BatchOutcome(requested, applied, firstFailIndex, firstFailReason, false);
    }

    /** One increment that really moved, kept only to undo it. */
    private record AppliedOp(StorageKey key, long signedAmount) {
    }

    /** {@return the configured cap on how many increments one API batch may carry} */
    public int batchMaxOps() {
        return config.behavior().batchMaxOps();
    }

    /**
     * Holds the withdrawal side of a batch without applying anything.
     *
     * <p>Only the withdrawal side is validated. Capacity is deliberately not pre-booked: a hold
     * exists to stop stock being spent twice, and freezing free slots as well would let one caller
     * starve every other deposit path for as long as it holds the ticket. Commit re-checks capacity.
     *
     * @param storage  the target storage
     * @param capacity the capacity breakdown, unused today but kept for symmetry with commit
     * @param ops      the increments whose withdrawals should be held
     * @param ttl      how long the hold survives without a commit
     * @return the new reservation id, or empty when stock is insufficient
     */
    public Optional<UUID> reserve(PlayerStorage storage,
            StorageCapacity capacity,
            List<StorageBatchOp> ops,
            Duration ttl) {
        Map<StorageKey, Long> projected = new HashMap<>();
        List<StorageReservation.Op> holds = new ArrayList<>(ops.size());
        storage.pruneExpiredReservations(System.currentTimeMillis());
        for (StorageBatchOp op : ops) {
            if (op.delta() == 0L) {
                return Optional.empty();
            }
            StorageKey key = StorageKey.of(op.template());
            holds.add(new StorageReservation.Op(key, op.delta()));
            if (!op.withdrawal()) {
                continue;
            }
            long stock = projected.computeIfAbsent(key, candidate -> availableAmount(storage, candidate));
            if (stock < op.magnitude()) {
                return Optional.empty();
            }
            projected.put(key, stock - op.magnitude());
        }
        UUID reservationId = UUID.randomUUID();
        storage.addReservation(new StorageReservation(reservationId,
                System.currentTimeMillis() + Math.max(1L, ttl.toMillis()), holds));
        return Optional.of(reservationId);
    }

    /**
     * Applies a held reservation.
     *
     * @param outcome       the batch outcome, or {@code null} when the reservation was unknown
     * @param allOrNothing  always {@code true}: a reservation is an all-or-nothing promise
     */
    public record CommitOutcome(BatchOutcome outcome, boolean allOrNothing) {
    }

    /**
     * Drops the hold and applies its increments in one owner-thread pass.
     *
     * <p>The hold is removed <em>before</em> applying, or {@link #availableAmount} would still exclude
     * the very units this commit is supposed to take.
     *
     * @param storage       the target storage
     * @param player        the online storage owner, used only to recompute capacity
     * @param capacity      the capacity breakdown captured before the commit
     * @param reservationId the hold to commit
     * @param source        the originating surface
     * @return the outcome, with a {@code null} batch outcome when the hold is unknown or expired
     */
    public CommitOutcome commitReservation(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            UUID reservationId,
            StorageOperationSource source) {
        StorageReservation reservation = storage.reservations().get(reservationId);
        if (reservation == null || reservation.expired(System.currentTimeMillis())) {
            storage.removeReservation(reservationId);
            return new CommitOutcome(null, true);
        }
        storage.removeReservation(reservationId);
        List<StorageBatchOp> ops = new ArrayList<>(reservation.ops().size());
        for (StorageReservation.Op op : reservation.ops()) {
            ops.add(new StorageBatchOp(op.key().toItemStack(), op.delta()));
        }
        BatchOutcome outcome = applyBatch(storage, player, capacity, ops, true, source);
        if (outcome.failed() || outcome.cancelled()) {
            // Put the hold back: a failed commit must not silently release the stock it was holding.
            storage.addReservation(reservation);
        }
        return new CommitOutcome(outcome, true);
    }

    /** Where and why a batch pre-check refused. */
    private record BatchRejection(int index, String reasonKey) {
    }

    /**
     * Validates the whole batch against a projection of the entry table.
     *
     * <p>The projection is what makes "any shortfall aborts everything" honest for a batch that lists
     * the same template several times: two withdrawals of 40 against a stock of 60 must fail, and they
     * only do so if the second op sees the first one's effect.
     *
     * @return the first rejection, or {@code null} when every op is applicable
     */
    private BatchRejection preCheckBatch(PlayerStorage storage,
            StorageCapacity capacity,
            List<StorageBatchOp> ops,
            List<StorageKey> keys) {
        AppConfig active = config;
        Map<StorageKey, Long> projected = new HashMap<>();
        Set<StorageKey> created = new HashSet<>();
        int freeSlots = Math.max(0, capacity.effectiveSlots() - capacity.usedSlots());
        for (int index = 0; index < ops.size(); index++) {
            StorageBatchOp op = ops.get(index);
            StorageKey key = keys.get(index);
            if (op.delta() == 0L) {
                return new BatchRejection(index, "invalid_delta");
            }
            StorageEntry entry = storage.entry(key);
            long stock = projected.computeIfAbsent(key, candidate -> availableAmount(storage, candidate));
            if (op.withdrawal()) {
                if (stock < op.magnitude()) {
                    return new BatchRejection(index, "insufficient_stock");
                }
                projected.put(key, stock - op.magnitude());
                continue;
            }
            ItemStack template = key.toItemStack();
            if (!passesFilter(template, active.behavior().depositFilter())) {
                return new BatchRejection(index, "filtered");
            }
            if (entry == null && !active.behavior().allowUniqueItems() && isUnique(template)) {
                return new BatchRejection(index, "unique_rejected");
            }
            if (entry == null && created.add(key) && created.size() > freeSlots) {
                return new BatchRejection(index, "no_free_slot");
            }
            int remainingSlots = Math.max(0, freeSlots - created.size());
            long ceiling = capacityService.spanCeiling(storage, entry, remainingSlots);
            if (stock > ceiling - op.magnitude()) {
                return new BatchRejection(index, "slot_full");
            }
            projected.put(key, stock + op.magnitude());
        }
        return null;
    }

    /**
     * {@return how many units of {@code key} a batch may actually take}
     *
     * <p>Reserved units are stored and visible but already promised to an outstanding reservation, so
     * they are excluded here rather than in the caller: a batch that could spend them would let the
     * same units be handed out twice.
     */
    private long availableAmount(PlayerStorage storage, StorageKey key) {
        StorageEntry entry = storage.entry(key);
        if (entry == null) {
            return 0L;
        }
        return Math.max(0L, entry.amount() - storage.reservedAmount(key));
    }

    /** Debits without pruning the entry, so an undo can restore it in place. */
    private long debitForBatch(PlayerStorage storage, StorageKey key, long amount) {
        StorageEntry entry = storage.entry(key);
        if (entry == null) {
            return 0L;
        }
        long takeable = Math.min(amount, availableAmount(storage, key));
        return takeable <= 0L ? 0L : entry.remove(takeable);
    }

    private void undo(PlayerStorage storage, List<AppliedOp> undoLog, Set<StorageKey> preexisting) {
        for (int index = undoLog.size() - 1; index >= 0; index--) {
            AppliedOp entryOp = undoLog.get(index);
            StorageEntry entry = storage.entry(entryOp.key());
            if (entry == null) {
                continue;
            }
            if (entryOp.signedAmount() < 0L) {
                entry.add(-entryOp.signedAmount(), Long.MAX_VALUE);
            } else {
                entry.remove(entryOp.signedAmount());
            }
            if (entry.empty() && !preexisting.contains(entryOp.key())) {
                storage.remove(entryOp.key());
            }
        }
    }

    private static List<Long> zeros(int size) {
        List<Long> zeros = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            zeros.add(0L);
        }
        return zeros;
    }

    private boolean fireBatchEvent(PlayerStorage storage, List<StorageBatchOp> ops,
            boolean allOrNothing, StorageOperationSource source) {
        StorageBatchEvent event = new StorageBatchEvent(storage.playerId(), ops, allOrNothing, source.id());
        event.callEvent();
        return !event.isCancelled();
    }

    private void logWithdraw(PlayerStorage storage, StorageKey key, long applied,
            StorageOperationSource source) {
        StorageEntry entry = storage.entry(key);
        operationLog.record(StorageLogEntry.of(storage.playerId(), StorageOperationType.WITHDRAW,
                textIndexer.identifierOf(key), -applied,
                entry == null ? 0L : entry.amount(), source, uniqueNote(key)));
        DebugLogger dl = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (dl != null) {
            dl.log("storage", storage.playerId(), "common.storage.withdraw", Map.of(
                    "player", storage.playerId().toString(),
                    "item", textIndexer.identifierOf(key),
                    "amount", String.valueOf(applied),
                    "balance", String.valueOf(entry == null ? 0L : entry.amount())));
        }
    }

    /**
     * Pre-transaction validation shared by every deposit path.
     *
     * @param room      how many units may still be accepted
     * @param ceiling   the total amount this entry is allowed to reach, already accounting for the
     *                  slots it may grow into under {@code behavior.multi_slot_stacking}
     * @param entry     the existing entry, {@code null} when a new one is needed
     * @param rejection a reason key when the deposit is refused outright, otherwise {@code null}
     */
    private record PreCheck(long room, long ceiling, StorageEntry entry, String rejection) {
    }

    private PreCheck preCheck(PlayerStorage storage, StorageKey key, StorageCapacity capacity) {
        AppConfig active = config;
        ItemStack template = key.toItemStack();
        if (!passesFilter(template, active.behavior().depositFilter())) {
            return new PreCheck(0L, 0L, null, "filtered");
        }
        StorageEntry entry = storage.entry(key);
        if (entry == null) {
            if (!active.behavior().allowUniqueItems() && isUnique(template)) {
                return new PreCheck(0L, 0L, null, "unique_rejected");
            }
            if (!capacityService.hasFreeSlot(capacity)) {
                return new PreCheck(0L, 0L, null, "no_free_slot");
            }
            // A brand new entry may also span several slots straight away, otherwise depositing 120
            // into an empty warehouse would still store 100 and reject 20.
            long ceiling = capacityService.spanCeiling(storage, null, capacity.freeSlots());
            return new PreCheck(ceiling, ceiling, null, null);
        }
        long ceiling = capacityService.spanCeiling(storage, entry, capacity.freeSlots());
        long room = ceiling >= Long.MAX_VALUE ? Long.MAX_VALUE - entry.amount() : ceiling - entry.amount();
        return new PreCheck(Math.max(0L, room), ceiling, entry, null);
    }

    private long credit(PlayerStorage storage, StorageKey key, PreCheck preCheck, long amount) {
        StorageEntry entry = preCheck.entry() != null ? preCheck.entry() : storage.entry(key);
        if (entry == null) {
            long accepted = Math.min(amount, preCheck.ceiling());
            if (accepted <= 0L) {
                return 0L;
            }
            storage.append(textIndexer.createEntry(key, accepted, 0L));
            return accepted;
        }
        return entry.add(amount, preCheck.ceiling());
    }

    /**
     * Builds a removal plan matched by full {@link ItemStack} equality.
     *
     * <p>CoreLib's {@code planRemoval} matches by ItemSource, which is the wrong identity here:
     * this module deduplicates on full component equality, so two items sharing a source but
     * differing in components must not be treated as interchangeable. The plan is still applied
     * through CoreLib's {@code applyRemoval}, keeping its compare-before-write guarantee.
     */
    private InventoryItemUtil.RemovalPlan planExactRemoval(PlayerInventory inventory,
            StorageKey key, long amount) {
        ItemStack[] contents = inventory.getContents();
        ItemStack target = key.toItemStack();
        List<InventoryItemUtil.SlotRemoval> removals = new ArrayList<>();
        long remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0L; slot++) {
            ItemStack candidate = contents[slot];
            if (candidate == null || candidate.getType().isAir() || !candidate.isSimilar(target)) {
                continue;
            }
            int take = (int) Math.min(remaining, candidate.getAmount());
            if (take <= 0) {
                continue;
            }
            ItemStack after = candidate.clone();
            after.setAmount(candidate.getAmount() - take);
            removals.add(new InventoryItemUtil.SlotRemoval(slot, candidate.clone(),
                    after.getAmount() <= 0 ? null : after));
            remaining -= take;
        }
        return new InventoryItemUtil.RemovalPlan(amount, amount - remaining, removals);
    }

    private boolean passesFilter(ItemStack template, AppConfig.DepositFilter filter) {
        if (filter.mode() == AppConfig.FilterMode.OFF || filter.entries().isEmpty()) {
            return filter.mode() != AppConfig.FilterMode.WHITELIST;
        }
        boolean listed = matchesAnyToken(template, filter.entries());
        return filter.mode() == AppConfig.FilterMode.WHITELIST ? listed : !listed;
    }

    private boolean matchesAnyToken(ItemStack template, List<String> tokens) {
        ItemSourceRef actual = itemSourceService == null ? null : itemSourceService.identifyItem(template);
        String materialKey = template.getType().getKey().value().toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals(materialKey)) {
                return true;
            }
            ItemSourceRef parsed = ItemSourceUtil.parse(normalized);
            if (parsed != null && actual != null && ItemSourceUtil.matches(parsed, actual)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@return whether the item carries a per-instance marker}
     *
     * <p>Detected structurally through the item's own persistent data rather than by reading its
     * display name or lore, which are rendering output and not an identity source.
     */
    private boolean isUnique(ItemStack template) {
        if (!template.hasItemMeta()) {
            return false;
        }
        var meta = template.getItemMeta();
        return meta != null && !meta.getPersistentDataContainer().isEmpty();
    }

    private String uniqueNote(StorageKey key) {
        ItemStack template = key.toItemStack();
        if (!isUnique(template)) {
            return null;
        }
        return "unique=" + Integer.toHexString(key.hashCode());
    }

    private boolean fireDepositEvent(PlayerStorage storage, StorageKey key, long amount,
            StorageOperationSource source) {
        StorageDepositEvent event = new StorageDepositEvent(storage.playerId(),
                key.toItemStack(), amount, source.id());
        event.callEvent();
        return !event.isCancelled();
    }

    private boolean fireWithdrawEvent(PlayerStorage storage, StorageKey key, long amount,
            long stored, StorageOperationSource source) {
        StorageWithdrawEvent event = new StorageWithdrawEvent(storage.playerId(),
                key.toItemStack(), amount, stored, source.id());
        event.callEvent();
        return !event.isCancelled();
    }

    private void logDeposit(PlayerStorage storage, StorageKey key, long applied,
            StorageOperationSource source) {
        StorageEntry entry = storage.entry(key);
        operationLog.record(StorageLogEntry.of(storage.playerId(), StorageOperationType.DEPOSIT,
                textIndexer.identifierOf(key), applied,
                entry == null ? 0L : entry.amount(), source, uniqueNote(key)));
        DebugLogger dl = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (dl != null) {
            dl.log("storage", storage.playerId(), "common.storage.deposit", Map.of(
                    "player", storage.playerId().toString(),
                    "item", textIndexer.identifierOf(key),
                    "amount", String.valueOf(applied),
                    "balance", String.valueOf(entry == null ? 0L : entry.amount())));
        }
    }
}
