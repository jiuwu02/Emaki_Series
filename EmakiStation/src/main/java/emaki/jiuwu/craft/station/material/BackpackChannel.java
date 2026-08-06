package emaki.jiuwu.craft.station.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;

/**
 * Reads and consumes materials directly from a player's own inventory.
 *
 * <h2>Why this no longer works on GUI slots</h2>
 * Materials used to live in a station window's input slots, which meant the whole class operated on a
 * {@code Map<Integer, ItemStack>} the GUI owned, and the window teardown path had to hand those items back on
 * close, on disconnect, and on disable. The catalog-style station never takes custody of anything: it reads
 * the player's real inventory and debits it at submit time. There is consequently nothing to give back, and no
 * window in which a rendered display stack could be mistaken for a real item.
 *
 * <p>Everything here runs on the owner thread and uses CoreLib's plan/apply/rollback, so a shortfall part-way
 * through restores the exact prior contents rather than leaving a half-consumed inventory.
 */
public final class BackpackChannel {

    private final ItemSourceService itemSourceService;

    /**
     * Creates the channel.
     *
     * @param itemSourceService CoreLib's item-source service, used for identity resolution
     */
    public BackpackChannel(ItemSourceService itemSourceService) {
        this.itemSourceService = itemSourceService;
    }

    /**
     * Counts how many units of one identity a player is carrying.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param player the player to inspect
     * @param source the identity to count
     * @return the units carried; zero when the player or source is unusable
     */
    public long count(Player player, ItemSourceRef source) {
        if (player == null || source == null || itemSourceService == null) {
            return 0L;
        }
        return InventoryItemUtil.countItems(player, itemSourceService, source);
    }

    /**
     * Counts every requested identity in one inventory pass.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param player  the player to inspect
     * @param sources the identities to count
     * @return the counts per identity; identities absent from the inventory map to zero
     */
    public Map<ItemSourceRef, Long> countAll(Player player, Iterable<ItemSourceRef> sources) {
        Map<ItemSourceRef, Long> counts = new LinkedHashMap<>();
        if (player == null || sources == null || itemSourceService == null) {
            return counts;
        }
        for (ItemSourceRef source : sources) {
            if (source == null || counts.containsKey(source)) {
                continue;
            }
            counts.put(source, InventoryItemUtil.countItems(player, itemSourceService, source));
        }
        return counts;
    }

    /**
     * Debits an exact set of amounts from a player's inventory.
     *
     * <p>All-or-nothing: every removal is planned and applied in order, and a single shortfall rolls back
     * every plan already applied. A caller therefore never has to compensate a partial debit.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param player  the player to debit
     * @param amounts the units to take per identity
     * @return the debited materials, or {@code null} when the inventory did not cover the request
     */
    public List<ConsumedMaterial> consume(Player player, Map<ItemSourceRef, Long> amounts) {
        if (player == null || amounts == null || itemSourceService == null) {
            return null;
        }
        PlayerInventory inventory = player.getInventory();
        List<InventoryItemUtil.RemovalPlan> applied = new ArrayList<>();
        List<ConsumedMaterial> consumed = new ArrayList<>();
        for (Map.Entry<ItemSourceRef, Long> entry : amounts.entrySet()) {
            ItemSourceRef source = entry.getKey();
            long needed = entry.getValue() == null ? 0L : entry.getValue();
            if (source == null || needed <= 0L) {
                continue;
            }
            InventoryItemUtil.RemovalPlan plan =
                    InventoryItemUtil.planRemoval(inventory, itemSourceService, source, needed);
            if (plan == null || plan.removedAmount() < needed) {
                rollback(inventory, applied);
                return null;
            }
            if (!InventoryItemUtil.applyRemoval(inventory, plan)) {
                rollback(inventory, applied);
                return null;
            }
            applied.add(plan);
            consumed.add(new ConsumedMaterial(source, needed, MaterialChannel.BACKPACK));
        }
        return consumed;
    }

    /**
     * Returns refunded materials to the player, dropping what will not fit.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param player the receiving player
     * @param source the item identity to return
     * @param amount the units to return
     * @return how many units were actually created and handed back
     */
    public long refund(Player player, ItemSourceRef source, long amount) {
        if (player == null || source == null || amount <= 0L || itemSourceService == null) {
            return 0L;
        }
        ItemStack template = itemSourceService.createItem(source, 1);
        if (template == null || template.getType().isAir()) {
            return 0L;
        }
        int maxStack = Math.max(1, template.getMaxStackSize());
        long remaining = amount;
        long delivered = 0L;
        while (remaining > 0L) {
            int chunk = (int) Math.min(remaining, maxStack);
            ItemStack stack = itemSourceService.createItem(source, chunk);
            if (stack == null || stack.getType().isAir()) {
                break;
            }
            InventoryItemUtil.addOrDrop(player, stack);
            delivered += chunk;
            remaining -= chunk;
        }
        return delivered;
    }

    private void rollback(PlayerInventory inventory, List<InventoryItemUtil.RemovalPlan> applied) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            InventoryItemUtil.rollbackRemoval(inventory, applied.get(index));
        }
        applied.clear();
    }
}
