package emaki.jiuwu.craft.dungeon.tempitem;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class TempItemManager {

    private final MessageService messages;
    private final DebugLogger debug;
    private final Map<String, Map<UUID, Map<String, Integer>>> granted = new ConcurrentHashMap<>();

    public TempItemManager(MessageService messages, DebugLogger debug) {
        this.messages = messages;
        this.debug = debug;
    }

    public void grant(String instanceId, Player player, String itemId, int amount) {
        if (instanceId == null || player == null || itemId == null || itemId.isBlank()) {
            return;
        }
        ItemStack stack = resolve(itemId, Math.max(1, amount));
        if (stack == null) {
            return;
        }
        var leftover = player.getInventory().addItem(stack);
        granted.computeIfAbsent(instanceId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
                .merge(itemId, Math.max(1, amount), Integer::sum);
        debug.log("dungeon", player, "temp_item_granted", Map.of(
                "instance", instanceId,
                "item", itemId,
                "amount", String.valueOf(amount)
        ));
        if (!leftover.isEmpty()) {
            messages.send(player, "flow.temp_item_full", Map.of("item", itemId));
        }
    }

    public void removeAll(String instanceId, Player player) {
        if (instanceId == null || player == null) {
            return;
        }
        Map<UUID, Map<String, Integer>> byInstance = granted.get(instanceId);
        if (byInstance == null) {
            return;
        }
        Map<String, Integer> byItem = byInstance.remove(player.getUniqueId());
        if (byItem == null || byItem.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : byItem.entrySet()) {
            removeItem(player, entry.getKey(), entry.getValue());
        }
        debug.log("dungeon", player, "temp_item_removed", Map.of(
                "instance", instanceId,
                "items", String.join(",", byItem.keySet())
        ));
    }

    public void clearInstance(String instanceId) {
        if (instanceId != null) {
            granted.remove(instanceId);
        }
    }

    private void removeItem(Player player, String itemId, int amount) {
        ItemStack match = resolve(itemId, 1);
        if (match == null) {
            return;
        }
        Material material = match.getType();
        int remaining = amount;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (remaining <= 0) {
                return;
            }
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
        }
    }

    private ItemStack resolve(String itemId, int amount) {
        try {
            Material material = Material.valueOf(itemId.toUpperCase(Locale.ROOT));
            if (material.isItem()) {
                return new ItemStack(material, amount);
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            var result = EmakiCoreLibApi.createConfiguredItem(
                    new ConfiguredItemDefinition(itemId, amount, Map.of()));
            if (result != null && result.success() && result.itemStack() != null) {
                return result.itemStack();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
