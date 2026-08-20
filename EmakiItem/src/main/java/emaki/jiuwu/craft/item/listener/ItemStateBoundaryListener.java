package emaki.jiuwu.craft.item.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.ItemStateConfig;
import emaki.jiuwu.craft.item.service.ItemStatePreservationService;

public final class ItemStateBoundaryListener implements Listener {

    private final EmakiItemPlugin plugin;

    public ItemStateBoundaryListener(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!policy().repairOnDrop()) {
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (preservation().repairBoundary(dropped, event.getPlayer(), "drop")
                == ItemStatePreservationService.Outcome.RESTORED) {
            event.getItemDrop().setItemStack(dropped);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!policy().repairOnPickup() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack picked = event.getItem().getItemStack();
        if (preservation().repairBoundary(picked, player, "pickup")
                == ItemStatePreservationService.Outcome.RESTORED) {
            event.getItem().setItemStack(picked);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryTransfer(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStateConfig.Preservation policy = policy();
        boolean merchant = event.getInventory().getType() == InventoryType.MERCHANT;
        if (merchant ? !policy.repairOnTrade() : !policy.repairOnContainerTransfer()) {
            return;
        }
        if (!crossesBoundary(event)) {
            return;
        }
        String reason = merchant ? "trade" : "container_transfer";
        ItemStack current = event.getCurrentItem();
        if (preservation().repairBoundary(current, player, reason)
                == ItemStatePreservationService.Outcome.RESTORED) {
            event.setCurrentItem(current);
        }
        ItemStack cursor = event.getCursor();
        if (preservation().repairBoundary(cursor, player, reason)
                == ItemStatePreservationService.Outcome.RESTORED) {
            event.getView().setCursor(cursor);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!policy().repairOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (preservation().repairBoundary(item, player, "join")
                    == ItemStatePreservationService.Outcome.RESTORED) {
                inventory.setItem(slot, item);
            }
        }
    }

    private boolean crossesBoundary(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.NOTHING) {
            return false;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return false;
        }
        return event.getView().getTopInventory().getSize() > 0
                && (clicked == event.getView().getTopInventory() || clicked instanceof PlayerInventory);
    }

    private ItemStateConfig.Preservation policy() {
        return plugin.stateService().config().preservation();
    }

    private ItemStatePreservationService preservation() {
        return plugin.statePreservation();
    }
}
