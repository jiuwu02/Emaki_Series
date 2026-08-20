package emaki.jiuwu.craft.gem.listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

public final class GemItemObtainListener implements Listener {

    private final EmakiGemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();

    public GemItemObtainListener(EmakiGemPlugin plugin, EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRefreshes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldChange(PlayerItemHeldEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        refreshLater(event.getPlayer());
    }

    private void refreshLater(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingRefreshes.add(playerId)) {
            return;
        }
        var task = scheduling.runForEntity(plugin, player, () -> {
            try {
                refreshInventory(plugin, player);
            } finally {
                pendingRefreshes.remove(playerId);
            }
        }, () -> pendingRefreshes.remove(playerId));
        if (task.cancelled()) {
            pendingRefreshes.remove(playerId);
        }
    }

    public static void refreshInventory(EmakiGemPlugin plugin, Player player) {
        if (plugin == null || player == null || plugin.stateService() == null || plugin.itemMatcher() == null || plugin.itemFactory() == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack refreshed = refreshItem(plugin, player, current);
            if (shouldReplace(current, refreshed)) {
                inventory.setItem(slot, refreshed);
                changed = true;
            }
        }
        ItemStack cursorItem = player.getItemOnCursor();
        ItemStack refreshedCursor = refreshItem(plugin, player, cursorItem);
        if (shouldReplace(cursorItem, refreshedCursor)) {
            player.setItemOnCursor(refreshedCursor);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private static boolean shouldReplace(ItemStack current, ItemStack refreshed) {
        if (refreshed == current) {
            return false;
        }
        if (current == null || current.getType().isAir()) {
            return refreshed != null && !refreshed.getType().isAir();
        }
        if (refreshed == null || refreshed.getType().isAir()) {
            return true;
        }
        return current.getAmount() != refreshed.getAmount() || !current.isSimilar(refreshed);
    }

    private static ItemStack refreshItem(EmakiGemPlugin plugin, Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return itemStack;
        }
        GemItemInstance gemInstance = plugin.itemMatcher().readStoredGemInstance(itemStack);
        if (gemInstance != null) {
            ItemStack refreshedGem = plugin.itemFactory().recreateGemItem(gemInstance, Math.max(1, itemStack.getAmount()));
            return refreshedGem == null ? itemStack : refreshedGem;
        }
        GemState storedState = plugin.stateService().readStoredState(itemStack);
        GemItemDefinition definition = storedState == null
                ? plugin.stateService().resolveItemDefinition(itemStack)
                : (plugin.gemItemLoader() == null ? null : plugin.gemItemLoader().get(storedState.itemDefinitionId()));
        if (definition == null) {
            return itemStack;
        }
        ItemStack refreshed = storedState == null
                ? plugin.stateService().applyInitialState(player, itemStack, definition)
                : plugin.stateService().applyState(itemStack, definition, plugin.stateService().resolveState(itemStack, definition));
        return refreshed == null ? itemStack : refreshed;
    }
}
