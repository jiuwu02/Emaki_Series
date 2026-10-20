package emaki.jiuwu.craft.dungeon.rewardbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.service.MessageService;

public final class RewardBoxService {

    private static final int MAX_PAGE_NODES = 32;
    private static final int CONTENT_SLOTS = 45;

    private final RewardBoxStore store;
    private final MessageService messages;

    public RewardBoxService(RewardBoxStore store, MessageService messages) {
        this.store = store;
        this.messages = messages;
    }

    public RewardBoxStore store() {
        return store;
    }

    public void deposit(UUID playerId, List<ItemStack> items) {
        if (playerId == null || items == null || items.isEmpty()) {
            return;
        }
        RewardBoxStore.Box box = store.load(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (box.autoFlow() && player != null) {
            List<ItemStack> leftover = new ArrayList<>();
            for (ItemStack stack : items) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                    leftover.add(rest);
                }
            }
            box.items().addAll(leftover);
            store.saveAsync(playerId, box);
            return;
        }
        for (ItemStack stack : items) {
            if (stack != null && stack.getAmount() > 0) {
                box.items().add(stack);
            }
        }
        store.saveAsync(playerId, box);
    }

    public boolean isAutoFlow(UUID playerId) {
        return store.load(playerId).autoFlow();
    }

    public void setAutoFlow(UUID playerId, boolean autoFlow) {
        RewardBoxStore.Box box = store.load(playerId);
        box.setAutoFlow(autoFlow);
        store.saveAsync(playerId, box);
    }

    public int maxPages(Player player) {
        for (int pages = MAX_PAGE_NODES; pages > 1; pages--) {
            if (player.hasPermission("emakidungeon.rewardbox.pages." + pages)) {
                return pages;
            }
        }
        return 1;
    }

    public int visibleCapacity(Player player) {
        return maxPages(player) * CONTENT_SLOTS;
    }

    public int contentSlots() {
        return CONTENT_SLOTS;
    }

    public void notifyStored(Player player, String itemLabel, int amount) {
        messages.send(player, "drop.box_stored", Map.of(
                "item", itemLabel,
                "amount", String.valueOf(amount)
        ));
    }
}
