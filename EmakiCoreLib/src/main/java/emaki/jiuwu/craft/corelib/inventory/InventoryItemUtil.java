package emaki.jiuwu.craft.corelib.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;

public final class InventoryItemUtil {

    private InventoryItemUtil() {
    }

    public static long countItems(Player player, ItemSourceService itemSourceService, String itemToken) {
        return countItems(player, itemSourceService, ItemSourceUtil.parse(itemToken));
    }

    public static long countItems(Player player, ItemSourceService itemSourceService, ItemSourceRef targetSource) {
        if (player == null) {
            return 0L;
        }
        return countItems(player.getInventory() == null ? null : player.getInventory().getContents(), itemSourceService, targetSource);
    }

    public static long countItems(ItemStack[] contents, ItemSourceService itemSourceService, String itemToken) {
        return countItems(contents, itemSourceService, ItemSourceUtil.parse(itemToken));
    }

    public static long countItems(ItemStack[] contents, ItemSourceService itemSourceService, ItemSourceRef targetSource) {
        if (contents == null || contents.length == 0 || itemSourceService == null || targetSource == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack itemStack : contents) {
            if (isEmptyStack(itemStack)) {
                continue;
            }
            ItemSourceRef source = itemSourceService.identifyItem(itemStack);
            if (ItemSourceUtil.matches(targetSource, source)) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    public static long countItems(Map<Integer, ItemStack> items, ItemSourceService itemSourceService, ItemSourceRef targetSource) {
        if (items == null || items.isEmpty() || itemSourceService == null || targetSource == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack itemStack : items.values()) {
            if (isEmptyStack(itemStack)) {
                continue;
            }
            ItemSourceRef source = itemSourceService.identifyItem(itemStack);
            if (ItemSourceUtil.matches(targetSource, source)) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    public static boolean removeItems(PlayerInventory inventory,
            ItemSourceService itemSourceService,
            String itemToken,
            long amount) {
        return removeItems(inventory, itemSourceService, ItemSourceUtil.parse(itemToken), amount);
    }

    public static boolean removeItems(PlayerInventory inventory,
            ItemSourceService itemSourceService,
            ItemSourceRef targetSource,
            long amount) {
        if (inventory == null || itemSourceService == null || targetSource == null || amount <= 0L) {
            return amount <= 0L;
        }
        RemovalPlan plan = planRemoval(inventory.getContents(), itemSourceService, targetSource, amount);
        return plan.complete() && applyRemoval(inventory, plan);
    }

    public static long removeItems(Map<Integer, ItemStack> items,
            ItemSourceService itemSourceService,
            ItemSourceRef targetSource,
            long amount) {
        if (amount <= 0L) {
            return amount;
        }
        if (items == null || items.isEmpty() || itemSourceService == null || targetSource == null) {
            return amount;
        }
        RemovalPlan plan = planRemoval(items, itemSourceService, targetSource, amount);
        return plan.complete() && applyRemoval(items, plan) ? 0L : amount;
    }

    public static RemovalPlan planRemoval(PlayerInventory inventory,
            ItemSourceService itemSourceService,
            ItemSourceRef targetSource,
            long amount) {
        return planRemoval(inventory == null ? null : inventory.getContents(), itemSourceService, targetSource, amount);
    }

    public static RemovalPlan planRemoval(ItemStack[] contents,
            ItemSourceService itemSourceService,
            ItemSourceRef targetSource,
            long amount) {
        if (contents == null || contents.length == 0 || itemSourceService == null || targetSource == null || amount <= 0L) {
            return RemovalPlan.empty(amount);
        }
        long remaining = amount;
        List<SlotRemoval> removals = new ArrayList<>();
        for (int slot = 0; slot < contents.length && remaining > 0L; slot++) {
            ItemStack itemStack = contents[slot];
            if (isEmptyStack(itemStack)) {
                continue;
            }
            ItemSourceRef source = itemSourceService.identifyItem(itemStack);
            if (!ItemSourceUtil.matches(targetSource, source)) {
                continue;
            }
            int take = (int) Math.min(remaining, itemStack.getAmount());
            if (take <= 0) {
                continue;
            }
            removals.add(slotRemoval(slot, itemStack, take));
            remaining -= take;
        }
        return new RemovalPlan(amount, amount - remaining, removals);
    }

    public static RemovalPlan planRemoval(Map<Integer, ItemStack> items,
            ItemSourceService itemSourceService,
            ItemSourceRef targetSource,
            long amount) {
        if (items == null || items.isEmpty() || itemSourceService == null || targetSource == null || amount <= 0L) {
            return RemovalPlan.empty(amount);
        }
        long remaining = amount;
        List<SlotRemoval> removals = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            if (remaining <= 0L) {
                break;
            }
            ItemStack itemStack = entry.getValue();
            if (isEmptyStack(itemStack)) {
                continue;
            }
            ItemSourceRef source = itemSourceService.identifyItem(itemStack);
            if (!ItemSourceUtil.matches(targetSource, source)) {
                continue;
            }
            int take = (int) Math.min(remaining, itemStack.getAmount());
            if (take <= 0) {
                continue;
            }
            removals.add(slotRemoval(entry.getKey(), itemStack, take));
            remaining -= take;
        }
        return new RemovalPlan(amount, amount - remaining, removals);
    }

    public static boolean applyRemoval(PlayerInventory inventory, RemovalPlan plan) {
        if (inventory == null || plan == null) {
            return false;
        }
        ItemStack[] contents = inventory.getContents();
        if (!matchesBefore(contents, plan)) {
            return false;
        }
        ItemStack[] updated = cloneContents(contents);
        for (SlotRemoval removal : plan.removals()) {
            updated[removal.slot()] = cloneNonAir(removal.after());
        }
        inventory.setContents(updated);
        return true;
    }

    public static boolean rollbackRemoval(PlayerInventory inventory, RemovalPlan plan) {
        if (inventory == null || plan == null) {
            return false;
        }
        ItemStack[] contents = inventory.getContents();
        if (!matchesAfter(contents, plan)) {
            return false;
        }
        ItemStack[] restored = cloneContents(contents);
        for (SlotRemoval removal : plan.removals()) {
            restored[removal.slot()] = cloneNonAir(removal.before());
        }
        inventory.setContents(restored);
        return true;
    }

    public static boolean applyRemoval(Map<Integer, ItemStack> items, RemovalPlan plan) {
        if (items == null || plan == null || !matchesBefore(items, plan)) {
            return false;
        }
        for (SlotRemoval removal : plan.removals()) {
            putStack(items, removal.slot(), removal.after());
        }
        return true;
    }

    public static boolean rollbackRemoval(Map<Integer, ItemStack> items, RemovalPlan plan) {
        if (items == null || plan == null || !matchesAfter(items, plan)) {
            return false;
        }
        for (SlotRemoval removal : plan.removals()) {
            putStack(items, removal.slot(), removal.before());
        }
        return true;
    }

    private static SlotRemoval slotRemoval(int slot, ItemStack itemStack, int take) {
        ItemStack before = cloneNonAir(itemStack);
        ItemStack after = cloneNonAir(itemStack);
        if (after != null) {
            after.setAmount(after.getAmount() - take);
        }
        return new SlotRemoval(slot, before, cloneNonAir(after));
    }

    private static boolean matchesBefore(ItemStack[] contents, RemovalPlan plan) {
        for (SlotRemoval removal : plan.removals()) {
            if (removal.slot() < 0 || removal.slot() >= contents.length
                    || !sameStack(contents[removal.slot()], removal.before())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAfter(ItemStack[] contents, RemovalPlan plan) {
        for (SlotRemoval removal : plan.removals()) {
            if (removal.slot() < 0 || removal.slot() >= contents.length
                    || !sameStack(contents[removal.slot()], removal.after())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesBefore(Map<Integer, ItemStack> items, RemovalPlan plan) {
        for (SlotRemoval removal : plan.removals()) {
            if (!sameStack(items.get(removal.slot()), removal.before())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAfter(Map<Integer, ItemStack> items, RemovalPlan plan) {
        for (SlotRemoval removal : plan.removals()) {
            if (!sameStack(items.get(removal.slot()), removal.after())) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            clone[slot] = cloneNonAir(contents[slot]);
        }
        return clone;
    }

    private static void putStack(Map<Integer, ItemStack> items, int slot, ItemStack itemStack) {
        ItemStack clone = cloneNonAir(itemStack);
        if (clone == null) {
            items.remove(slot);
        } else {
            items.put(slot, clone);
        }
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        ItemStack left = cloneNonAir(first);
        ItemStack right = cloneNonAir(second);
        return left == null ? right == null : right != null
                && left.getAmount() == right.getAmount()
                && left.isSimilar(right);
    }

    private static boolean isEmptyStack(ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty();
    }

    public static Map<Integer, ItemStack> addOrDrop(Player player, ItemStack itemStack) {
        ItemStack clone = cloneNonAir(itemStack);
        if (player == null || clone == null) {
            return Map.of();
        }
        Map<Integer, ItemStack> leftover = new LinkedHashMap<>(player.getInventory().addItem(clone));
        for (ItemStack left : leftover.values()) {
            ItemStack drop = cloneNonAir(left);
            if (drop != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        return leftover.isEmpty() ? Map.of() : Map.copyOf(leftover);
    }

    public static void giveOrDrop(Player player, ItemStack itemStack) {
        addOrDrop(player, itemStack);
    }

    public static ItemStack cloneNonAir(ItemStack itemStack) {
        if (isEmptyStack(itemStack)) {
            return null;
        }
        return itemStack.clone();
    }

    public static <T> TemporaryHandsResult<T> withTemporaryHands(Player player,
            ItemStack mainHand,
            ItemStack offHand,
            Supplier<T> supplier) {
        ItemStack originalMainHand = cloneNonAir(player.getInventory().getItemInMainHand());
        ItemStack originalOffHand = cloneNonAir(player.getInventory().getItemInOffHand());
        try {
            player.getInventory().setItemInMainHand(cloneNonAir(mainHand));
            player.getInventory().setItemInOffHand(cloneNonAir(offHand));
            T result = supplier.get();
            ItemStack updatedMainHand = cloneNonAir(player.getInventory().getItemInMainHand());
            ItemStack updatedOffHand = cloneNonAir(player.getInventory().getItemInOffHand());
            return new TemporaryHandsResult<>(result, updatedMainHand, updatedOffHand);
        } finally {
            player.getInventory().setItemInMainHand(originalMainHand);
            player.getInventory().setItemInOffHand(originalOffHand);
        }
    }

    public record SlotRemoval(int slot, ItemStack before, ItemStack after) {

        public SlotRemoval {
            before = cloneNonAir(before);
            after = cloneNonAir(after);
        }
    }

    public record RemovalPlan(long requestedAmount, long removedAmount, List<SlotRemoval> removals) {

        public RemovalPlan {
            requestedAmount = Math.max(0L, requestedAmount);
            removedAmount = Math.max(0L, Math.min(requestedAmount, removedAmount));
            removals = removals == null ? List.of() : List.copyOf(removals);
        }

        public static RemovalPlan empty(long requestedAmount) {
            return new RemovalPlan(requestedAmount, 0L, List.of());
        }

        public long remainingAmount() {
            return Math.max(0L, requestedAmount - removedAmount);
        }

        public boolean complete() {
            return remainingAmount() == 0L;
        }
    }

    public record TemporaryHandsResult<T>(T result, ItemStack updatedMainHand, ItemStack updatedOffHand) {

    }
}
