package emaki.jiuwu.craft.storage.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.api.model.StorageResult;
import emaki.jiuwu.craft.storage.config.AutoPickupConfig;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.model.PlayerStorage;

/**
 * 自动拾取。
 *
 * <p>两种模式共用同一段「尝试转入仓库」逻辑：
 * {@code ON_PICKUP} 由拾取监听器驱动，{@code RADIUS} 由周期任务扫描附近掉落物。
 *
 * <p>转入只在**全量成功**时才吞掉掉落物；部分成功或失败一律交还原版拾取。
 * 这样不需要回写地上物品的数量，也就不存在数量算错导致刷物品或吞物品的风险。
 */
public final class StorageAutoPickupService {

    /** 拾取权限；未声明在描述符中的按玩家开关另由 meta 保存。 */
    public static final String PERMISSION = "emakistorage.autopickup";

    private final EmakiStoragePlugin plugin;
    private final Map<UUID, Long> notifyCooldowns = new ConcurrentHashMap<>();
    private TaskHandle scanTask;

    public StorageAutoPickupService(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    /** 按配置启动或停止周期扫描；可重复调用，reload 时用它切换模式。 */
    public void configure() {
        stop();
        AutoPickupConfig config = config();
        if (!config.enabled() || !config.radiusMode()) {
            return;
        }
        int interval = config.scanIntervalTicks();
        scanTask = plugin.executionDispatcher().runGlobalTimer(plugin, this::scanAll, interval, interval);
    }

    /** 停止周期扫描并清理提示节流状态。 */
    public void stop() {
        if (scanTask != null) {
            try {
                scanTask.cancel();
            } catch (RuntimeException _) {
                // 任务可能已结束，忽略
            }
            scanTask = null;
        }
        notifyCooldowns.clear();
    }

    /** {@return 玩家当前是否启用了自动拾取} */
    public boolean isActiveFor(Player player) {
        AutoPickupConfig config = config();
        if (!config.enabled() || player == null || !player.hasPermission(PERMISSION)) {
            return false;
        }
        PlayerStorage storage = plugin.dataStore().cached(player.getUniqueId());
        return storage != null && storage.autoPickupEnabled();
    }

    /**
     * 尝试把一份物品整单转入仓库。
     *
     * <p>必须在该玩家的所有者线程调用。
     *
     * @param player 目标玩家
     * @param stack  待转入的物品
     * @return 全量转入成功返回 {@code true}；调用方此时应移除掉落物
     */
    public boolean tryDepositAll(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return false;
        }
        if (!isActiveFor(player)) {
            return false;
        }
        // 仓库尚未加载完时放行，避免登录期的异步窗口吞掉物品。
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

    /**
     * 吸取玩家附近的掉落物。
     *
     * <p>两阶段提交：先在掉落物所在区域线程确认可拾取并取走它，再回到玩家线程入库；
     * 入库失败时把物品原地掉回。这样在 Folia 的跨区域场景下不会出现物品既在地上
     * 又进了仓库的窗口。
     */
    private void collectNearby(Player player, double radius) {
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
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
        // 只吸取无主或属于该玩家的掉落物，避免抢走别人的战利品。
        UUID owner = item.getOwner();
        if (owner != null && !owner.equals(player.getUniqueId())) {
            return;
        }
        ItemStack stack = item.getItemStack().clone();
        if (stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        org.bukkit.Location dropLocation = item.getLocation().clone();
        item.remove();
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            if (!tryDepositAll(player, stack)) {
                restore(dropLocation, stack);
            }
        }, () -> restore(dropLocation, stack));
    }

    /** 入库失败时把物品掉回原处，保证零损失。 */
    private void restore(org.bukkit.Location location, ItemStack stack) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        plugin.executionDispatcher().runAtLocation(plugin, location,
                () -> location.getWorld().dropItem(location, stack));
    }

    /** 按节流窗口提示玩家仓库存不下，拾取是高频事件，不能每次都发。 */
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
