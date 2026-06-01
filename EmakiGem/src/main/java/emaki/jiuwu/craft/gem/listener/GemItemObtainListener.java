package emaki.jiuwu.craft.gem.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

public final class GemItemObtainListener implements Listener {

    private final EmakiGemPlugin plugin;

    public GemItemObtainListener(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer());
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
        FoliaSchedulerAdapter.runEntityTask(plugin, player, () -> refreshInventory(plugin, player));
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
            if (refreshed != current) {
                inventory.setItem(slot, refreshed);
                changed = true;
            }
        }
        ItemStack cursorItem = player.getItemOnCursor();
        ItemStack refreshedCursor = refreshItem(plugin, player, cursorItem);
        if (refreshedCursor != cursorItem) {
            player.setItemOnCursor(refreshedCursor);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
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
