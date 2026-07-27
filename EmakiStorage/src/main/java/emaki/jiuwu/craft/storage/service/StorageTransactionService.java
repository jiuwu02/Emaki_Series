package emaki.jiuwu.craft.storage.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.storage.api.event.StorageDepositEvent;
import emaki.jiuwu.craft.storage.api.event.StorageWithdrawEvent;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.api.model.StorageResult;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

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

    private volatile AppConfig config;

    public StorageTransactionService(ItemSourceService itemSourceService,
            StorageCapacityService capacityService,
            StorageTextIndexer textIndexer,
            StorageOperationLog operationLog,
            AppConfig config) {
        this.itemSourceService = itemSourceService;
        this.capacityService = capacityService;
        this.textIndexer = textIndexer;
        this.operationLog = operationLog;
        this.config = config;
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
     * Pre-transaction validation shared by every deposit path.
     *
     * @param room      how many units may still be accepted
     * @param entry     the existing entry, {@code null} when a new one is needed
     * @param rejection a reason key when the deposit is refused outright, otherwise {@code null}
     */
    private record PreCheck(long room, StorageEntry entry, String rejection) {
    }

    private PreCheck preCheck(PlayerStorage storage, StorageKey key, StorageCapacity capacity) {
        AppConfig active = config;
        ItemStack template = key.toItemStack();
        if (!passesFilter(template, active.behavior().depositFilter())) {
            return new PreCheck(0L, null, "filtered");
        }
        StorageEntry entry = storage.entry(key);
        if (entry == null) {
            if (!active.behavior().allowUniqueItems() && isUnique(template)) {
                return new PreCheck(0L, null, "unique_rejected");
            }
            if (!capacityService.hasFreeSlot(capacity)) {
                return new PreCheck(0L, null, "no_free_slot");
            }
            long limit = capacityService.effectiveStackLimit(storage, null);
            return new PreCheck(limit, null, null);
        }
        long limit = capacityService.effectiveStackLimit(storage, entry);
        long room = limit >= Long.MAX_VALUE ? Long.MAX_VALUE - entry.amount() : limit - entry.amount();
        return new PreCheck(Math.max(0L, room), entry, null);
    }

    private long credit(PlayerStorage storage, StorageKey key, PreCheck preCheck, long amount) {
        StorageEntry entry = preCheck.entry() != null ? preCheck.entry() : storage.entry(key);
        if (entry == null) {
            long limit = capacityService.effectiveStackLimit(storage, null);
            long accepted = Math.min(amount, limit);
            if (accepted <= 0L) {
                return 0L;
            }
            storage.append(textIndexer.createEntry(key, accepted, 0L));
            return accepted;
        }
        long limit = capacityService.effectiveStackLimit(storage, entry);
        return entry.add(amount, limit);
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
        ItemSource actual = itemSourceService == null ? null : itemSourceService.identifyItem(template);
        String materialKey = template.getType().getKey().value().toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals(materialKey)) {
                return true;
            }
            ItemSource parsed = ItemSourceUtil.parse(normalized);
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
    }
}
