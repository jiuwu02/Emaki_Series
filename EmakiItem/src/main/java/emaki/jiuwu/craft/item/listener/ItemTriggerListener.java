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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class ItemTriggerListener implements Listener {

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
                run(player, definition, "left_click", Map.of());
                run(player, definition, "left_click_air", Map.of());
                if (player.isSneaking()) {
                    run(player, definition, "shift_left_click", Map.of());
                }
            }
            case LEFT_CLICK_BLOCK -> {
                run(player, definition, "left_click", Map.of());
                if (player.isSneaking()) {
                    run(player, definition, "shift_left_click", Map.of());
                }
            }
            case RIGHT_CLICK_AIR -> {
                run(player, definition, "right_click", Map.of());
                run(player, definition, "right_click_air", Map.of());
                if (player.isSneaking()) {
                    run(player, definition, "shift_right_click", Map.of());
                }
            }
            case RIGHT_CLICK_BLOCK -> {
                run(player, definition, "right_click", Map.of());
                run(player, definition, "right_click_block", Map.of());
                if (player.isSneaking()) {
                    run(player, definition, "shift_right_click", Map.of());
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        EmakiItemDefinition definition = definition(event.getItemDrop().getItemStack());
        if (definition == null || !passes(event.getPlayer(), definition, "drop")) {
            return;
        }
        run(event.getPlayer(), definition, "drop", Map.of());
        if (event.getPlayer().isSneaking()) {
            run(event.getPlayer(), definition, "shift_drop", Map.of());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        EmakiItemDefinition definition = definition(event.getMainHandItem());
        if (definition == null || !passes(event.getPlayer(), definition, "swap_hand")) {
            return;
        }
        run(event.getPlayer(), definition, "swap_hand", Map.of());
        if (event.getPlayer().isSneaking()) {
            run(event.getPlayer(), definition, "shift_swap_hand", Map.of());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, "sneak")) {
            run(event.getPlayer(), definition, "sneak", Map.of());
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
            run(player, definition, "death", Map.of());
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
        EmakiItemDefinition definition = definition(event.getItemInHand());
        if (definition != null && passes(event.getPlayer(), definition, "place_block")) {
            run(event.getPlayer(), definition, "place_block", Map.of("block", event.getBlock().getType().name()));
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

    private EmakiItemDefinition held(Player player) {
        return player == null ? null : definition(player.getInventory().getItemInMainHand());
    }

    private EmakiItemDefinition definition(ItemStack itemStack) {
        String id = plugin.identifier().identify(itemStack);
        return id.isBlank() ? null : plugin.itemLoader().get(id);
    }

    private boolean passes(Player player, EmakiItemDefinition definition, String trigger) {
        return plugin.conditionChecker().passes(player, definition, trigger);
    }

    private void run(Player player, EmakiItemDefinition definition, String trigger, Map<String, ?> placeholders) {
        plugin.actionService().execute(player, definition, trigger, placeholders);
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
