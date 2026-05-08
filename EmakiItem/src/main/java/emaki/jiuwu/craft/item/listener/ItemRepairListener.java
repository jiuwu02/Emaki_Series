package emaki.jiuwu.craft.item.listener;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.RepairConfig;
import emaki.jiuwu.craft.item.model.RepairMaterial;
import emaki.jiuwu.craft.item.service.ItemRepairService;

/**
 * Listens for inventory click events to handle repairing disabled emaki items.
 * When a player clicks a disabled emaki item with a valid repair material on cursor,
 * the repair is performed and the material is consumed.
 */
public final class ItemRepairListener implements Listener {

    private final EmakiItemPlugin plugin;
    private final ItemRepairService repairService;

    public ItemRepairListener(EmakiItemPlugin plugin, ItemRepairService repairService) {
        this.plugin = plugin;
        this.repairService = repairService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        // Check if the clicked item is a disabled emaki item
        if (!repairService.isDisabled(clicked)) {
            return;
        }
        String id = plugin.identifier().identify(clicked);
        if (id.isBlank()) {
            return;
        }
        EmakiItemDefinition definition = plugin.itemLoader().get(id);
        if (definition == null) {
            return;
        }
        RepairConfig repairConfig = definition.repair();
        if (!repairConfig.enabled()) {
            return;
        }
        // Find matching repair material
        RepairMaterial matched = repairService.findMatchingMaterial(definition, cursor);
        if (matched == null) {
            return;
        }
        // Check if cursor has enough amount
        if (cursor.getAmount() < matched.amount()) {
            return;
        }
        // Perform repair
        int consumed = repairService.repair(player, clicked, cursor, matched);
        if (consumed <= 0) {
            return;
        }
        // Consume repair material from cursor
        int remaining = cursor.getAmount() - consumed;
        if (remaining <= 0) {
            event.getView().setCursor(null);
        } else {
            cursor.setAmount(remaining);
        }

        // Execute on_repaired actions
        if (!repairConfig.onRepairedActions().isEmpty()) {
            plugin.actionService().executeLines(
                    player,
                    definition,
                    "on_repaired",
                    repairConfig.onRepairedActions(),
                    Map.of("item_id", definition.id())
            );
        }

        // Cancel the event to prevent normal click behavior
        event.setCancelled(true);
    }
}
