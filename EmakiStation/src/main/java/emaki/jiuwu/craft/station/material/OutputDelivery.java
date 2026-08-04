package emaki.jiuwu.craft.station.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.api.model.PendingOutput;

/**
 * Routes finished outputs to the warehouse, the inventory, or a pending claim.
 *
 * <p>Stacks are always rebuilt from the item identity and split by the item's own maximum stack size. A
 * display-rendered stack is never handed to a player, because its presentation lore and rewritten
 * stack-size component would be burned into a real item permanently.
 *
 * <p>The warehouse can refuse a deposit for a reason that has nothing to do with capacity: with
 * {@code behavior.allow_unique_items} off it rejects any item carrying PDC, which is exactly what a custom
 * output tends to be. That refusal is treated like any other full destination and falls through to the next
 * target rather than being reported as an internal error, so the player gets their items and a clear
 * message instead of a silent loss.
 */
public final class OutputDelivery {

    private final ItemSourceService itemSourceService;
    private final StorageChannel storageChannel;

    /**
     * Creates the router.
     *
     * @param itemSourceService CoreLib's item-source service, used to rebuild stacks
     * @param storageChannel    the warehouse channel
     */
    public OutputDelivery(ItemSourceService itemSourceService, StorageChannel storageChannel) {
        this.itemSourceService = itemSourceService;
        this.storageChannel = storageChannel;
    }

    /**
     * The result of one delivery attempt.
     *
     * @param delivered outputs that reached the player
     * @param pending   outputs that could not be delivered and now await a claim
     */
    public record Result(List<PendingOutput> delivered, List<PendingOutput> pending) {

        /**
         * Creates a result with defensively copied lists.
         *
         * @param delivered outputs that reached the player; {@code null} becomes empty
         * @param pending   outputs awaiting a claim; {@code null} becomes empty
         */
        public Result {
            delivered = delivered == null ? List.of() : List.copyOf(delivered);
            pending = pending == null ? List.of() : List.copyOf(pending);
        }

        /** {@return whether anything still awaits a claim} */
        public boolean hasPending() {
            return !pending.isEmpty();
        }
    }

    /**
     * Delivers outputs according to a routing preference.
     *
     * <p><strong>Thread:</strong> the target player's owner thread, because the inventory leg touches the
     * player directly. The warehouse leg is awaited internally; its continuation only aggregates numbers and
     * never touches Bukkit state.
     *
     * @param player  the receiving player
     * @param outputs the outputs to deliver
     * @param routing where to try first
     * @return a future carrying what was delivered and what is still owed
     */
    public CompletableFuture<Result> deliverAsync(Player player,
            List<PendingOutput> outputs,
            OutputRouting routing) {
        if (player == null || outputs == null || outputs.isEmpty()) {
            return CompletableFuture.completedFuture(new Result(List.of(), List.of()));
        }
        OutputRouting effective = routing == null ? OutputRouting.STORAGE_FIRST : routing;
        if (effective.prefersStorage() && storageAvailable()) {
            return deliverToStorage(player, outputs).thenApply(remaining -> {
                if (remaining.isEmpty()) {
                    return new Result(outputs, List.of());
                }
                if (!effective.allowsBackpack()) {
                    return new Result(subtract(outputs, remaining), remaining);
                }
                List<PendingOutput> stillOwed = deliverToBackpack(player, remaining);
                return new Result(subtract(outputs, stillOwed), stillOwed);
            });
        }
        if (effective.allowsBackpack()) {
            List<PendingOutput> remaining = deliverToBackpack(player, outputs);
            if (remaining.isEmpty() || !effective.allowsStorage() || !storageAvailable()) {
                return CompletableFuture.completedFuture(new Result(subtract(outputs, remaining), remaining));
            }
            return deliverToStorage(player, remaining)
                    .thenApply(stillOwed -> new Result(subtract(outputs, stillOwed), stillOwed));
        }
        if (effective.allowsStorage() && storageAvailable()) {
            return deliverToStorage(player, outputs)
                    .thenApply(stillOwed -> new Result(subtract(outputs, stillOwed), stillOwed));
        }
        return CompletableFuture.completedFuture(new Result(List.of(), outputs));
    }

    private boolean storageAvailable() {
        return storageChannel != null && storageChannel.usable();
    }

    private CompletableFuture<List<PendingOutput>> deliverToStorage(Player player,
            List<PendingOutput> outputs) {
        Map<ItemSourceRef, Long> amounts = new LinkedHashMap<>();
        for (PendingOutput output : outputs) {
            amounts.merge(output.source(), output.amount(), Long::sum);
        }
        return storageChannel.depositAsync(player.getUniqueId(), amounts).thenApply(accepted -> {
            List<PendingOutput> remaining = new ArrayList<>();
            for (Map.Entry<ItemSourceRef, Long> entry : amounts.entrySet()) {
                long stored = accepted.getOrDefault(entry.getKey(), 0L);
                long owed = entry.getValue() - stored;
                if (owed > 0L) {
                    remaining.add(new PendingOutput(entry.getKey(), owed));
                }
            }
            return remaining;
        });
    }

    private List<PendingOutput> deliverToBackpack(Player player, List<PendingOutput> outputs) {
        List<PendingOutput> remaining = new ArrayList<>();
        for (PendingOutput output : outputs) {
            long owed = giveToInventory(player, output.source(), output.amount());
            if (owed > 0L) {
                remaining.add(output.withAmount(owed));
            }
        }
        return remaining;
    }

    /**
     * Places units into the inventory, stopping as soon as one chunk cannot be placed.
     *
     * <p>Stops rather than dropping so the remainder stays accounted for as a pending claim; silently
     * dropping a large output onto the ground is how items get lost.
     *
     * @param player the receiving player
     * @param source the identity to hand out
     * @param amount the units to hand out
     * @return how many units could not be placed
     */
    private long giveToInventory(Player player, ItemSourceRef source, long amount) {
        if (itemSourceService == null) {
            return amount;
        }
        ItemStack probe = itemSourceService.createItem(source, 1);
        if (probe == null || probe.getType().isAir()) {
            return amount;
        }
        int maxStack = Math.max(1, probe.getMaxStackSize());
        long remaining = amount;
        while (remaining > 0L) {
            int chunk = (int) Math.min(remaining, maxStack);
            ItemStack stack = itemSourceService.createItem(source, chunk);
            if (stack == null || stack.getType().isAir()) {
                return remaining;
            }
            if (player.getInventory().firstEmpty() < 0 && !fitsExisting(player, stack)) {
                return remaining;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            if (leftovers.isEmpty()) {
                remaining -= chunk;
                continue;
            }
            long unplaced = 0L;
            for (ItemStack leftover : leftovers.values()) {
                if (leftover != null) {
                    unplaced += leftover.getAmount();
                }
            }
            remaining -= chunk - unplaced;
            return Math.max(0L, remaining);
        }
        return 0L;
    }

    private static boolean fitsExisting(Player player, ItemStack stack) {
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing != null && existing.isSimilar(stack)
                    && existing.getAmount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private static List<PendingOutput> subtract(List<PendingOutput> requested,
            List<PendingOutput> remaining) {
        if (remaining.isEmpty()) {
            return List.copyOf(requested);
        }
        Map<ItemSourceRef, Long> owed = new LinkedHashMap<>();
        for (PendingOutput output : remaining) {
            owed.merge(output.source(), output.amount(), Long::sum);
        }
        List<PendingOutput> delivered = new ArrayList<>();
        for (PendingOutput output : requested) {
            long pending = owed.getOrDefault(output.source(), 0L);
            long stillOwed = Math.min(pending, output.amount());
            // Decrement unconditionally: when one identity appears in two requested entries, skipping the
            // update for a fully-pending entry would charge the same shortfall twice and under-report the
            // second entry's delivery.
            owed.put(output.source(), pending - stillOwed);
            long handed = output.amount() - stillOwed;
            if (handed > 0L) {
                delivered.add(output.withAmount(handed));
            }
        }
        return delivered;
    }

    /**
     * Delivers pending outputs during a claim, without any warehouse routing preference.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param player  the claiming player
     * @param outputs the owed outputs
     * @param routing the station's routing preference
     * @return a future carrying what was delivered and what is still owed
     */
    public CompletableFuture<Result> claimAsync(Player player,
            List<PendingOutput> outputs,
            OutputRouting routing) {
        return deliverAsync(player, outputs, routing);
    }
}
