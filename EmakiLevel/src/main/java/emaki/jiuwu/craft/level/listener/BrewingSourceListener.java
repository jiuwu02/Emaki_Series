package emaki.jiuwu.craft.level.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

public final class BrewingSourceListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;
    private final Map<String, BrewerUser> users = new ConcurrentHashMap<>();

    public BrewingSourceListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || event.getInventory().getType() != InventoryType.BREWING) {
            return;
        }
        recordUser(event.getInventory().getLocation(), player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getInventory().getType() != InventoryType.BREWING) {
            return;
        }
        recordUser(event.getInventory().getLocation(), player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        BrewerInventory inventory = event.getContents();
        Location location = inventory.getLocation();
        if (location == null && inventory.getHolder() instanceof BrewingStand stand) {
            location = stand.getLocation();
        }
        if (location == null) {
            return;
        }
        Location brewLocation = location.clone();
        FoliaSchedulerAdapter.runAtLocationLater(plugin, brewLocation, () -> awardDelayed(inventory, brewLocation), 1L);
    }

    private void awardDelayed(BrewerInventory inventory, Location location) {
        if (inventory == null || location == null) {
            return;
        }
        BrewerUser user = users.get(key(location));
        if (user == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(user.uuid());
        if (player == null) {
            return;
        }
        long ageTicks = Math.max(0L, (System.currentTimeMillis() - user.time()) / 50L);
        String potionType = firstPotionType(inventory.getContents());
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("brew_complete")) {
            if (ageTicks > Math.max(1, source.attributionExpireTicks())) {
                continue;
            }
            SourceRuleConfig.Rule rule = sourceService.matchPotion(source, potionType);
            if (rule != null) {
                sourceService.award(player, source, rule, Map.of("potion_type", potionType), "brew_complete");
            }
        }
    }

    private void recordUser(Location location, Player player) {
        if (location != null && player != null) {
            users.put(key(location), new BrewerUser(player.getUniqueId(), System.currentTimeMillis()));
        }
    }

    private String firstPotionType(ItemStack[] contents) {
        if (contents == null) {
            return "UNKNOWN";
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR || !(item.getItemMeta() instanceof PotionMeta meta)) {
                continue;
            }
            return meta.getBasePotionType() == null ? "UNKNOWN" : meta.getBasePotionType().name();
        }
        return "UNKNOWN";
    }

    private String key(Location location) {
        return (location.getWorld() == null ? "unknown" : location.getWorld().getUID()) + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record BrewerUser(UUID uuid, long time) {
    }
}
