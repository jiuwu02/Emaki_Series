package emaki.jiuwu.craft.item.listener;

import java.util.Map;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class ItemTriggerListener implements Listener {

    private static final org.bukkit.NamespacedKey DISABLED_KEY = new org.bukkit.NamespacedKey("emakiitem", "disabled");

    private static final Map<String, Object> EMPTY_PLACEHOLDERS = Map.of();

    private final EmakiItemPlugin plugin;

    public ItemTriggerListener(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        EmakiItemDefinition definition = held(player);
        if (definition == null || !passes(player, definition, "interact")) {
            return;
        }
        switch (event.getAction()) {
            case LEFT_CLICK_AIR -> {
                run(player, definition, "left_click", EMPTY_PLACEHOLDERS);
                run(player, definition, "left_click_air", EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, "shift_left_click", EMPTY_PLACEHOLDERS);
                }
            }
            case LEFT_CLICK_BLOCK -> {
                run(player, definition, "left_click", EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, "shift_left_click", EMPTY_PLACEHOLDERS);
                }
            }
            case RIGHT_CLICK_AIR -> {
                run(player, definition, "right_click", EMPTY_PLACEHOLDERS);
                run(player, definition, "right_click_air", EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, "shift_right_click", EMPTY_PLACEHOLDERS);
                }
            }
            case RIGHT_CLICK_BLOCK -> {
                run(player, definition, "right_click", EMPTY_PLACEHOLDERS);
                run(player, definition, "right_click_block", EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, "shift_right_click", EMPTY_PLACEHOLDERS);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        EmakiItemDefinition definition = definition(dropped);
        if (definition == null || !passes(event.getPlayer(), definition, "drop", dropped)) {
            return;
        }
        run(event.getPlayer(), definition, "drop", EMPTY_PLACEHOLDERS, dropped);
        if (event.getPlayer().isSneaking()) {
            run(event.getPlayer(), definition, "shift_drop", EMPTY_PLACEHOLDERS, dropped);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        ItemStack mainHandItem = event.getMainHandItem();
        EmakiItemDefinition definition = definition(mainHandItem);
        if (definition == null || !passes(event.getPlayer(), definition, "swap_hand", mainHandItem)) {
            return;
        }
        run(event.getPlayer(), definition, "swap_hand", EMPTY_PLACEHOLDERS, mainHandItem);
        if (event.getPlayer().isSneaking()) {
            run(event.getPlayer(), definition, "shift_swap_hand", EMPTY_PLACEHOLDERS, mainHandItem);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, "sneak")) {
            run(event.getPlayer(), definition, "sneak", EMPTY_PLACEHOLDERS);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = playerDamager(event.getDamager());
        if (attacker != null) {
            EmakiItemDefinition definition = held(attacker);
            if (definition != null) {
                if (!passes(attacker, definition, "attack")) {
                    event.setCancelled(true);
                } else {
                    Map<String, Object> placeholders = Map.of(
                            "target", event.getEntity().getName(),
                            "damage", event.getDamage()
                    );
                    run(attacker, definition, "left_click", placeholders);
                    run(attacker, definition, "left_click_entity", placeholders);
                    run(attacker, definition, "attack", placeholders);
                }
            }
        }
        if (event.getEntity() instanceof Player victim) {
            EmakiItemDefinition definition = held(victim);
            if (definition != null && passes(victim, definition, "damaged_by_entity")) {
                run(victim, definition, "damaged_by_entity", Map.of(
                        "attacker", event.getDamager().getName(),
                        "damage", event.getDamage()
                ));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof Player player)) {
            return;
        }
        EmakiItemDefinition definition = held(player);
        if (definition != null && passes(player, definition, "damaged")) {
            run(player, definition, "damaged", Map.of("damage", event.getDamage()));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        EmakiItemDefinition definition = held(killer);
        if (definition == null || !passes(killer, definition, "kill_entity")) {
            return;
        }
        run(killer, definition, event.getEntity() instanceof Player ? "kill_player" : "kill_entity",
                Map.of("target", event.getEntity().getName()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EmakiItemDefinition definition = held(player);
        if (definition != null && passes(player, definition, "death")) {
            run(player, definition, "death", EMPTY_PLACEHOLDERS);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            EmakiItemDefinition definition = held(player);
            if (definition != null && passes(player, definition, "shoot_bow")) {
                run(player, definition, "shoot_bow", Map.of("projectile_type", event.getProjectile().getType().name()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player player) {
            EmakiItemDefinition definition = held(player);
            if (definition != null && passes(player, definition, "shoot_trident")) {
                run(player, definition, "shoot_trident", Map.of("projectile_type", trident.getType().name()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        EmakiItemDefinition definition = held(player);
        if (definition == null || !passes(player, definition, "projectile_hit")) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (projectile instanceof Trident) {
            run(player, definition, event.getHitEntity() == null ? "trident_land" : "trident_hit", Map.of("projectile_type", "TRIDENT"));
        } else if (projectile instanceof AbstractArrow) {
            run(player, definition, event.getHitEntity() == null ? "arrow_land" : "arrow_hit", Map.of("projectile_type", projectile.getType().name()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, "break_block")) {
            run(event.getPlayer(), definition, "break_block", Map.of("block", event.getBlock().getType().name()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack placed = event.getItemInHand();
        EmakiItemDefinition definition = definition(placed);
        if (definition != null && passes(event.getPlayer(), definition, "place_block", placed)) {
            run(event.getPlayer(), definition, "place_block", Map.of("block", event.getBlock().getType().name()), placed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, "teleport")) {
            run(event.getPlayer(), definition, "teleport", Map.of());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, "login")) {
            run(event.getPlayer(), definition, "login", Map.of());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack consumed = event.getItem();
        EmakiItemDefinition definition = definition(consumed);
        if (definition == null || !passes(event.getPlayer(), definition, "consume", consumed)) {
            return;
        }
        run(event.getPlayer(), definition, "consume", Map.of(
                "consumed_item", consumed.getType().name()
        ), consumed);
    }

    private EmakiItemDefinition held(Player player) {
        return definition(heldItem(player));
    }

    private ItemStack heldItem(Player player) {
        return player == null ? null : player.getInventory().getItemInMainHand();
    }

    private EmakiItemDefinition definition(ItemStack itemStack) {
        String id = plugin.identifier().identify(itemStack);
        return id.isBlank() ? null : plugin.itemLoader().get(id);
    }

    private boolean passes(Player player, EmakiItemDefinition definition, String trigger) {
        return passes(player, definition, trigger, heldItem(player));
    }

    private boolean passes(Player player, EmakiItemDefinition definition, String trigger, ItemStack itemStack) {
        if (definition.repair().enabled() && isHeldItemDisabled(player)) {
            return false;
        }
        return plugin.conditionChecker().passes(player, definition, trigger, itemStack);
    }

    private boolean isHeldItemDisabled(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || !held.hasItemMeta()) {
            return false;
        }
        org.bukkit.persistence.PersistentDataContainer pdc = held.getItemMeta().getPersistentDataContainer();
        Byte value = pdc.get(DISABLED_KEY, org.bukkit.persistence.PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private void run(Player player, EmakiItemDefinition definition, String trigger, Map<String, ?> placeholders) {
        run(player, definition, trigger, placeholders, heldItem(player));
    }

    private void run(Player player, EmakiItemDefinition definition, String trigger, Map<String, ?> placeholders, ItemStack itemStack) {
        plugin.actionService().execute(player, definition, trigger, placeholders, itemStack);
    }

    private Player playerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return damager instanceof LivingEntity living && living.getKiller() != null ? living.getKiller() : null;
    }
}
