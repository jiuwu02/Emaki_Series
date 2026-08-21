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
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;

public final class BackpackChannel {

    public record SlotStack(int slotIndex, ItemStack stack) {
    }

    private final ItemSourceService itemSourceService;

    public BackpackChannel(ItemSourceService itemSourceService) {
        this.itemSourceService = itemSourceService;
    }

    public List<SlotStack> snapshotStacks(Player player) {
        if (player == null) {
            return List.of();
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        List<SlotStack> snapshot = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < contents.length; slotIndex++) {
            ItemStack stack = contents[slotIndex];
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            snapshot.add(new SlotStack(slotIndex, stack));
        }
        return List.copyOf(snapshot);
    }

    public ItemSourceRef identify(ItemStack stack) {
        return itemSourceService == null ? null : itemSourceService.identifyItem(stack);
    }

    public MatchContext contextOf(Player player, ItemStack stack) {
        return MatchContext.of(stack, identify(stack), player);
    }

    public long count(Player player, ItemSourceRef source) {
        if (player == null || source == null || itemSourceService == null) {
            return 0L;
        }
        return InventoryItemUtil.countItems(player, itemSourceService, source);
    }

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

    public List<ConsumedMaterial> consumeStacks(Player player,
            List<MergedMaterialChannel.StackDebit> debits) {
        if (player == null || debits == null) {
            return null;
        }
        if (debits.isEmpty()) {
            return List.of();
        }
        PlayerInventory inventory = player.getInventory();
        Map<Integer, Integer> deductions = new LinkedHashMap<>();
        for (MergedMaterialChannel.StackDebit debit : debits) {
            if (debit.amount() <= 0) {
                continue;
            }
            ItemStack current = inventory.getItem(debit.slotIndex());
            if (current == null || current.getType().isAir()) {
                return null;
            }
            if (!current.isSimilar(debit.expected())) {
                return null;
            }
            int planned = deductions.getOrDefault(debit.slotIndex(), 0) + debit.amount();
            if (planned > current.getAmount()) {
                return null;
            }
            deductions.put(debit.slotIndex(), planned);
        }
        Map<Integer, ItemStack> restore = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : deductions.entrySet()) {
            ItemStack current = inventory.getItem(entry.getKey());
            if (current == null) {
                rollbackSlots(inventory, restore);
                return null;
            }
            restore.put(entry.getKey(), current.clone());
            int remaining = current.getAmount() - entry.getValue();
            if (remaining <= 0) {
                inventory.setItem(entry.getKey(), null);
            } else {
                current.setAmount(remaining);
                inventory.setItem(entry.getKey(), current);
            }
        }
        Map<ItemSourceRef, Long> merged = new LinkedHashMap<>();
        for (MergedMaterialChannel.StackDebit debit : debits) {
            if (debit.amount() > 0) {
                merged.merge(debit.source(), (long) debit.amount(), Long::sum);
            }
        }
        List<ConsumedMaterial> consumed = new ArrayList<>(merged.size());
        merged.forEach((source, amount) ->
                consumed.add(new ConsumedMaterial(source, amount, MaterialChannel.BACKPACK)));
        return consumed;
    }

    private void rollbackSlots(PlayerInventory inventory, Map<Integer, ItemStack> restore) {
        restore.forEach(inventory::setItem);
        restore.clear();
    }

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
