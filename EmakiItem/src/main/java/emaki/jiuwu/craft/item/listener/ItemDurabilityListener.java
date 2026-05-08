package emaki.jiuwu.craft.item.listener;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.RepairConfig;
import emaki.jiuwu.craft.item.service.ItemRepairService;

/**
 * Listens for item durability damage events and disables emaki items
 * when their damage reaches or exceeds max_damage, instead of allowing them to break.
 */
public final class ItemDurabilityListener implements Listener {

    private final EmakiItemPlugin plugin;
    private final ItemRepairService repairService;

    public ItemDurabilityListener(EmakiItemPlugin plugin, ItemRepairService repairService) {
        this.plugin = plugin;
        this.repairService = repairService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack itemStack = event.getItem();
        String id = plugin.identifier().identify(itemStack);
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
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : itemStack.getType().getMaxDurability();
        if (maxDamage <= 0) {
            return;
        }
        int currentDamage = damageable.getDamage();
        int newDamage = currentDamage + event.getDamage();

        if (newDamage >= maxDamage) {
            event.setCancelled(true);

            // Set damage to max (visually broken)
            damageable.setDamage(maxDamage);
            itemStack.setItemMeta(meta);

            // Mark as disabled
            repairService.markDisabled(itemStack);

            // Execute on_disabled actions
            Player player = event.getPlayer();
            if (!repairConfig.onDisabledActions().isEmpty()) {
                plugin.actionService().executeLines(
                        player,
                        definition,
                        "on_disabled",
                        repairConfig.onDisabledActions(),
                        Map.of("item_id", definition.id())
                );
            }
        }
    }
}
