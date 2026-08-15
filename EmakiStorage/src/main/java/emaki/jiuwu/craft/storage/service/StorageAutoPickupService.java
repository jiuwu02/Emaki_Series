package emaki.jiuwu.craft.storage.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.model.StorageResult;
import emaki.jiuwu.craft.storage.config.AutoPickupConfig;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.model.PlayerStorage;

public final class StorageAutoPickupService {

    public static final String PERMISSION = "emakistorage.autopickup";

    private final EmakiStoragePlugin plugin;
    private final Map<UUID, Long> notifyCooldowns = new ConcurrentHashMap<>();
    private TaskToken scanTask;

    public StorageAutoPickupService(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    public void configure() {
        stop();
        AutoPickupConfig config = config();
        if (!config.enabled() || !config.radiusMode()) {
            return;
        }
        int interval = config.scanIntervalTicks();
        scanTask = plugin.executionDispatcher().runGlobalTimer(plugin, this::scanAll, interval, interval);
    }

    public void stop() {
        if (scanTask != null) {
            try {
                scanTask.cancel();
            } catch (RuntimeException _) {

            }
            scanTask = null;
        }
        notifyCooldowns.clear();
    }

    public boolean isActiveFor(Player player) {
        AutoPickupConfig config = config();
        if (!config.enabled() || player == null || !player.hasPermission(PERMISSION)) {
            return false;
        }
        PlayerStorage storage = plugin.dataStore().cached(player.getUniqueId());
        return storage != null && storage.autoPickupEnabled();
    }

    public boolean tryDepositAll(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return false;
        }
        if (!isActiveFor(player)) {
            return false;
        }

        PlayerStorage storage = plugin.dataStore().cached(player.getUniqueId());
        if (storage == null) {
            return false;
        }
        StorageResult result = plugin.transactionService().depositDirect(
                storage,
                player,
                plugin.capacityService().capacityOf(storage, player, plugin.appConfig().gui().storageRows() * 9),
                stack,
                stack.getAmount(),
                StorageOperationSource.AUTO_PICKUP
        );
        if (result.complete()) {
            return true;
        }
        notifyRejected(player, result);
        return false;
    }

    private void scanAll() {
        AutoPickupConfig config = config();
        if (!config.enabled() || !config.radiusMode()) {
            return;
        }
        double radius = config.radius();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isActiveFor(player)) {
                collectNearby(player, radius);
            }
        }
    }

    private void collectNearby(Player player, double radius) {
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            plugin.executionDispatcher().runEntity(plugin, item, () -> claimAndDeposit(player, item), () -> {
            });
        }
    }

    private void claimAndDeposit(Player player, Item item) {
        if (!item.isValid() || item.isDead() || item.getPickupDelay() > 0) {
            return;
        }

        UUID owner = item.getOwner();
        if (owner != null && !owner.equals(player.getUniqueId())) {
            return;
        }
        ItemStack stack = item.getItemStack().clone();
        if (stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        Location dropLocation = item.getLocation().clone();
        item.remove();
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            if (!tryDepositAll(player, stack)) {
                restore(dropLocation, stack);
            }
        }, () -> restore(dropLocation, stack));
    }

    private void restore(Location location, ItemStack stack) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        plugin.executionDispatcher().runAtLocation(plugin, location,
                () -> location.getWorld().dropItem(location, stack));
    }

    private void notifyRejected(Player player, StorageResult result) {
        AutoPickupConfig config = config();
        if (config.notifyCooldownMs() <= 0L || result.reasonKey() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = notifyCooldowns.get(player.getUniqueId());
        if (last != null && now - last < config.notifyCooldownMs()) {
            return;
        }
        notifyCooldowns.put(player.getUniqueId(), now);
        plugin.messageService().send(player, "auto_pickup.rejected");
    }

    private AutoPickupConfig config() {
        AutoPickupConfig config = plugin.appConfig() == null ? null : plugin.appConfig().autoPickup();
        return config == null ? AutoPickupConfig.defaults() : config;
    }
}
