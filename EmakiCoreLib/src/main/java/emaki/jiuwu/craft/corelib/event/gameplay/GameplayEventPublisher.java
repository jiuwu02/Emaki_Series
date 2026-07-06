package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.event.EmakiEventBus;

/**
 * The single Bukkit listener that captures common gameplay signals for the whole Emaki suite
 * and republishes them as {@link GameplayEvent}s on the shared {@link EmakiEventBus}.
 *
 * <p>This centralizes three pieces of logic that EmakiLevel and EmakiCodex previously each
 * duplicated:
 * <ul>
 *   <li><b>MythicMobs resolution</b> — reflective lookup of a dying entity's Mythic id/level,
 *       done once here instead of once per plugin;</li>
 *   <li><b>Last-damager attribution</b> — crediting a kill to the last player who damaged the
 *       victim when Bukkit reports no direct killer (projectiles, delayed deaths);</li>
 *   <li><b>Brew-stand user tracking</b> — remembering who last used a brewing stand so a
 *       finished brew can be attributed.</li>
 * </ul>
 *
 * <p>All handlers run at {@link EventPriority#MONITOR} (observe-only), matching the priority the
 * original per-plugin listeners used, so dispatch timing is unchanged. The publisher reads its
 * two attribution windows live from {@code configSupplier} on every use, so a CoreLib reload
 * takes effect without re-registration. Nothing here knows about experience, advancements,
 * anti-abuse, or conditions — subscribers own all downstream policy.
 */
public final class GameplayEventPublisher implements Listener {

    private final Plugin plugin;
    private final EmakiEventBus eventBus;
    private final Supplier<CoreLibConfig.GameplayEventConfig> configSupplier;

    private final Map<UUID, DamageAttribution> lastDamagers = new ConcurrentHashMap<>();
    private final Map<String, BrewerUser> brewers = new ConcurrentHashMap<>();

    public GameplayEventPublisher(Plugin plugin,
            EmakiEventBus eventBus,
            Supplier<CoreLibConfig.GameplayEventConfig> configSupplier) {
        this.plugin = plugin;
        this.eventBus = eventBus;
        this.configSupplier = configSupplier;
    }

    private CoreLibConfig.GameplayEventConfig config() {
        CoreLibConfig.GameplayEventConfig config = configSupplier.get();
        return config == null ? CoreLibConfig.GameplayEventConfig.defaults() : config;
    }

    private boolean disabled() {
        return !config().enabled();
    }

    // ------------------------------------------------------------------
    // Combat / kills
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (disabled() || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player player = playerDamager(event.getDamager());
        if (player != null) {
            lastDamagers.put(event.getEntity().getUniqueId(),
                    new DamageAttribution(player.getUniqueId(), System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        DamageAttribution attribution = lastDamagers.remove(entity.getUniqueId());
        if (disabled()) {
            return;
        }
        Player killer = entity.getKiller();
        boolean directKill = killer != null;
        if (killer == null && attribution != null
                && System.currentTimeMillis() - attribution.time() <= config().lastDamagerExpireTicks() * 50L) {
            killer = plugin.getServer().getPlayer(attribution.playerId());
        }
        if (killer == null) {
            return;
        }
        eventBus.publish(new EntityKillEvent(killer, entity, directKill));
        MythicMobInfo info = mythicMobInfo(entity);
        if (info != null) {
            eventBus.publish(new MythicKillEvent(killer, entity, info.mobId(), info.level()));
        }
    }

    private Player playerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (disabled()) {
            return;
        }
        eventBus.publish(new BlockPlaceGameplayEvent(event.getPlayer(), event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (disabled()) {
            return;
        }
        Block block = event.getBlock();
        eventBus.publish(new BlockBreakGameplayEvent(event.getPlayer(), block, isMature(block)));
    }

    private boolean isMature(Block block) {
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    // ------------------------------------------------------------------
    // Crafting / smelting
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (disabled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        eventBus.publish(new CraftGameplayEvent(player, result));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (disabled()) {
            return;
        }
        int amount = event.getItemAmount();
        ItemStack result = new ItemStack(event.getItemType(), Math.max(1, amount));
        eventBus.publish(new FurnaceExtractGameplayEvent(event.getPlayer(), result, amount));
    }

    // ------------------------------------------------------------------
    // Fishing / taming
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (disabled()) {
            return;
        }
        eventBus.publish(new FishGameplayEvent(event.getPlayer(), event.getState().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (disabled() || !(event.getOwner() instanceof Player player)) {
            return;
        }
        eventBus.publish(new TameGameplayEvent(player, event.getEntityType()));
    }

    // ------------------------------------------------------------------
    // Brewing (open/click record the user; brew completion is delayed one tick)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getType() == InventoryType.BREWING) {
            recordBrewer(event.getInventory().getLocation(), player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getInventory().getType() == InventoryType.BREWING) {
            recordBrewer(event.getInventory().getLocation(), player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (disabled()) {
            return;
        }
        BrewerInventory inventory = event.getContents();
        Location location = inventory.getLocation();
        if (location == null && inventory.getHolder() instanceof BrewingStand stand) {
            location = stand.getLocation();
        }
        if (location == null) {
            return;
        }
        Location brewLocation = location.clone();
        FoliaSchedulerAdapter.runAtLocationLater(plugin, brewLocation, () -> publishBrew(inventory, brewLocation), 1L);
    }

    private void publishBrew(BrewerInventory inventory, Location location) {
        if (inventory == null || location == null) {
            return;
        }
        BrewerUser user = brewers.get(locationKey(location));
        if (user == null) {
            return;
        }
        long ageTicks = Math.max(0L, (System.currentTimeMillis() - user.time()) / 50L);
        if (ageTicks > config().brewAttributionExpireTicks()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(user.uuid());
        if (player == null) {
            return;
        }
        eventBus.publish(new BrewGameplayEvent(player, firstPotionType(inventory.getContents()), ageTicks));
    }

    private void recordBrewer(Location location, Player player) {
        if (location != null && player != null) {
            brewers.put(locationKey(location), new BrewerUser(player.getUniqueId(), System.currentTimeMillis()));
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

    private String locationKey(Location location) {
        return (location.getWorld() == null ? "unknown" : location.getWorld().getUID())
                + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    // ------------------------------------------------------------------
    // MythicMobs reflection (soft dependency; no compile-time MythicMobs types)
    // ------------------------------------------------------------------

    private MythicMobInfo mythicMobInfo(LivingEntity entity) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            return null;
        }
        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object instance = mythicBukkit.getMethod("inst").invoke(null);
            Object mobManager = instance.getClass().getMethod("getMobManager").invoke(instance);
            Object activeMobs = mobManager.getClass().getMethod("getActiveMobs").invoke(mobManager);
            if (!(activeMobs instanceof Iterable<?> iterable)) {
                return null;
            }
            for (Object activeMob : iterable) {
                Object abstractEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
                Object bukkitEntity = abstractEntity.getClass().getMethod("getBukkitEntity").invoke(abstractEntity);
                if (bukkitEntity instanceof Entity bukkit && bukkit.getUniqueId().equals(entity.getUniqueId())) {
                    return new MythicMobInfo(resolveMobId(activeMob), resolveMobLevel(activeMob));
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
        return null;
    }

    private String resolveMobId(Object activeMob) {
        for (String method : List.of("getMobType", "getType", "getInternalName")) {
            try {
                Object value = activeMob.getClass().getMethod(method).invoke(activeMob);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            Object mythicMob = activeMob.getClass().getMethod("getType").invoke(activeMob);
            Object internal = mythicMob.getClass().getMethod("getInternalName").invoke(mythicMob);
            return String.valueOf(internal);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private double resolveMobLevel(Object activeMob) {
        for (String method : List.of("getLevel", "getMobLevel")) {
            try {
                Object value = activeMob.getClass().getMethod(method).invoke(activeMob);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return 1D;
    }

    private record DamageAttribution(UUID playerId, long time) {
    }

    private record BrewerUser(UUID uuid, long time) {
    }

    private record MythicMobInfo(String mobId, double level) {
    }
}
