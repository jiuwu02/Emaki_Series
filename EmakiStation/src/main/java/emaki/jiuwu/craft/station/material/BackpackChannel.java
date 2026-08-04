package emaki.jiuwu.craft.station.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * Reads and consumes materials the player placed into a station's input slots.
 *
 * <p>Everything here runs on the owner thread: input slots are plain {@link ItemStack}s held in the GUI
 * session, and consumption uses CoreLib's plan/apply/rollback so a failure mid-way restores the exact
 * slot contents rather than leaving a half-consumed input area.
 *
 * <p>Items in the input slots belong to the player. They are never destroyed on close or shutdown; the
 * session teardown path hands them back.
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
     * Aggregates the identities and counts currently held in the input slots.
     *
     * <p><strong>Thread:</strong> the viewing player's owner thread.
     *
     * @param inputs the input slot contents keyed by inventory slot
     * @return the available counts per identity; never {@code null}
     */
    public Map<ItemSourceRef, Long> available(Map<Integer, ItemStack> inputs) {
        Map<ItemSourceRef, Long> counts = new LinkedHashMap<>();
        if (inputs == null || inputs.isEmpty() || itemSourceService == null) {
            return counts;
        }
        for (ItemStack stack : inputs.values()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ItemSourceRef ref = itemSourceService.identifyItem(stack);
            if (ref == null) {
                continue;
            }
            counts.merge(ref, (long) stack.getAmount(), Long::sum);
        }
        return counts;
    }

    /**
     * Consumes a recipe's requirements from the input slots.
     *
     * <p>All-or-nothing: every requirement is planned first, and a single shortfall rolls back every plan
     * already applied. A caller therefore never has to compensate a partial consumption.
     *
     * <p><strong>Thread:</strong> the viewing player's owner thread.
     *
     * @param inputs the input slot contents, mutated in place on success
     * @param recipe the recipe being crafted
     * @param batch  how many times to apply the recipe
     * @return the debited materials, or {@code null} when the inputs did not cover the recipe
     */
    public List<ConsumedMaterial> consume(Map<Integer, ItemStack> inputs,
            RecipeDefinition recipe,
            long batch) {
        if (inputs == null || recipe == null || itemSourceService == null) {
            return null;
        }
        List<InventoryItemUtil.RemovalPlan> applied = new ArrayList<>();
        List<ConsumedMaterial> consumed = new ArrayList<>();
        for (MaterialRequirement requirement : recipe.requirements()) {
            long needed = requirement.totalFor(batch);
            if (!requirement.consume()) {
                if (!holds(inputs, requirement, needed)) {
                    rollback(inputs, applied);
                    return null;
                }
                continue;
            }
            for (ItemSourceRef source : requirement.sources()) {
                if (needed <= 0L) {
                    break;
                }
                InventoryItemUtil.RemovalPlan plan =
                        InventoryItemUtil.planRemoval(inputs, itemSourceService, source, needed);
                if (plan == null || plan.removedAmount() <= 0L) {
                    continue;
                }
                if (!InventoryItemUtil.applyRemoval(inputs, plan)) {
                    rollback(inputs, applied);
                    return null;
                }
                applied.add(plan);
                consumed.add(new ConsumedMaterial(source, plan.removedAmount(), MaterialChannel.BACKPACK));
                needed -= plan.removedAmount();
            }
            if (needed > 0L) {
                rollback(inputs, applied);
                return null;
            }
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

    private boolean holds(Map<Integer, ItemStack> inputs, MaterialRequirement requirement, long needed) {
        long total = 0L;
        for (ItemSourceRef source : requirement.sources()) {
            total += InventoryItemUtil.countItems(inputs, itemSourceService, source);
            if (total >= needed) {
                return true;
            }
        }
        return total >= needed;
    }

    private void rollback(Map<Integer, ItemStack> inputs, List<InventoryItemUtil.RemovalPlan> applied) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            InventoryItemUtil.rollbackRemoval(inputs, applied.get(index));
        }
        applied.clear();
    }
}
