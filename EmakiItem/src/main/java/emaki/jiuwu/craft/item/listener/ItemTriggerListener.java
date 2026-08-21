package emaki.jiuwu.craft.item.listener;

import java.util.Map;
import java.util.Set;

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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.trigger.TriggerRegistry;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.trigger.EquipmentSourceResolver;
import emaki.jiuwu.craft.item.trigger.ProficiencyGuard;
import emaki.jiuwu.craft.item.trigger.ProjectileSourceSnapshot;
import emaki.jiuwu.craft.item.trigger.ProjectileTriggerResolver;
import emaki.jiuwu.craft.item.ItemPdcKeys;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class ItemTriggerListener implements Listener {

    private static final Map<String, Object> EMPTY_PLACEHOLDERS = Map.of();

    private static final Set<String> WEAPON_SLOT = Set.of(EquipmentSlotMatcher.SLOT_MAIN_HAND);

    private static final Set<String> HAND_SLOTS = Set.of(
            EquipmentSlotMatcher.SLOT_MAIN_HAND,
            EquipmentSlotMatcher.SLOT_OFF_HAND);

    private final EmakiItemPlugin plugin;

    public ItemTriggerListener(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        EmakiItemDefinition definition = held(player);
        if (definition == null || !passes(player, definition, TriggerRegistry.LEFT_CLICK)) {
            return;
        }
        switch (event.getAction()) {
            case LEFT_CLICK_AIR -> {
                run(player, definition, TriggerRegistry.LEFT_CLICK, EMPTY_PLACEHOLDERS);
                run(player, definition, "left_click_air", EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, TriggerRegistry.SHIFT_LEFT_CLICK, EMPTY_PLACEHOLDERS);
                }
            }
            case LEFT_CLICK_BLOCK -> {
                run(player, definition, TriggerRegistry.LEFT_CLICK, EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, TriggerRegistry.SHIFT_LEFT_CLICK, EMPTY_PLACEHOLDERS);
                }
            }
            case RIGHT_CLICK_AIR -> {
                run(player, definition, TriggerRegistry.RIGHT_CLICK, EMPTY_PLACEHOLDERS);
                run(player, definition, "right_click_air", EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, TriggerRegistry.SHIFT_RIGHT_CLICK, EMPTY_PLACEHOLDERS);
                }
            }
            case RIGHT_CLICK_BLOCK -> {
                run(player, definition, TriggerRegistry.RIGHT_CLICK, EMPTY_PLACEHOLDERS);
                run(player, definition, "right_click_block", EMPTY_PLACEHOLDERS);
                if (player.isSneaking()) {
                    run(player, definition, TriggerRegistry.SHIFT_RIGHT_CLICK, EMPTY_PLACEHOLDERS);
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
        if (definition == null || !passes(event.getPlayer(), definition, TriggerRegistry.DROP_ITEM, dropped)) {
            return;
        }
        run(event.getPlayer(), definition, TriggerRegistry.DROP_ITEM, EMPTY_PLACEHOLDERS, dropped);
        if (event.getPlayer().isSneaking()) {
            run(event.getPlayer(), definition, TriggerRegistry.SHIFT_DROP_ITEM, EMPTY_PLACEHOLDERS, dropped);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        ItemStack mainHandItem = event.getMainHandItem();
        EmakiItemDefinition definition = definition(mainHandItem);
        if (definition == null || !passes(event.getPlayer(), definition, TriggerRegistry.SWAP_ITEMS, mainHandItem)) {
            return;
        }
        run(event.getPlayer(), definition, TriggerRegistry.SWAP_ITEMS, EMPTY_PLACEHOLDERS, mainHandItem);
        if (event.getPlayer().isSneaking()) {
            run(event.getPlayer(), definition, TriggerRegistry.SHIFT_SWAP_ITEMS, EMPTY_PLACEHOLDERS, mainHandItem);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, TriggerRegistry.SNEAK)) {
            run(event.getPlayer(), definition, TriggerRegistry.SNEAK, EMPTY_PLACEHOLDERS);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = playerDamager(event.getDamager());
        if (attacker != null) {
            EmakiItemDefinition definition = held(attacker);
            if (definition != null) {
                if (!passes(attacker, definition, TriggerRegistry.ATTACK)) {
                    event.setCancelled(true);
                } else {
                    Map<String, Object> placeholders = Map.of(
                            "target", event.getEntity().getName(),
                            "damage", event.getDamage()
                    );
                    ItemStack item = heldItem(attacker);
                    run(attacker, definition, TriggerRegistry.LEFT_CLICK, placeholders, item);
                    run(attacker, definition, "left_click_entity", placeholders, item);
                    run(attacker, definition, TriggerRegistry.ATTACK, placeholders, item);
                }
            }
        }
        if (event.getEntity() instanceof Player victim) {
            Map<String, Object> victimPlaceholders = Map.of(
                    "attacker", event.getDamager().getName(),
                    "damage", event.getDamage()
            );
            EmakiItemDefinition definition = held(victim);
            if (definition != null && passes(victim, definition, TriggerRegistry.DAMAGED_BY_ENTITY)) {
                run(victim, definition, TriggerRegistry.DAMAGED_BY_ENTITY, victimPlaceholders);
            }
            runForDefensiveSlots(victim, TriggerRegistry.DAMAGED_BY_ENTITY, victimPlaceholders);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0D || !event.getEntity().isValid()) {
            return;
        }
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null || !attacker.isValid() || attacker.equals(event.getEntity())) {
            return;
        }
        Map<String, Object> placeholders = Map.of(
                "target", event.getEntity().getName(),
                "damage", event.getFinalDamage()
        );
        ProficiencyGuard.Session guard = plugin.proficiencyGuard()
                .session(attacker, "damage_dealt", targetIdentity(event.getEntity()));
        ItemStack weapon = damageSourceItem(attacker, event.getDamager());
        EmakiItemDefinition weaponDefinition = definition(weapon);
        if (weaponDefinition != null
                && guard.admits(weaponDefinition.id(), EquipmentSlotMatcher.SLOT_MAIN_HAND)
                && passes(attacker, weaponDefinition, "damage_dealt", weapon)) {
            run(attacker, weaponDefinition, "damage_dealt", placeholders, weapon);
        }
        runForArmorSlots(attacker, "damage_dealt", placeholders, guard);
    }

    private String targetIdentity(Entity target) {
        return target == null ? "" : target.getUniqueId().toString();
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Map<String, Object> placeholders = Map.of("damage", event.getDamage());
        EmakiItemDefinition definition = held(player);
        if (definition != null && passes(player, definition, TriggerRegistry.DAMAGED)) {
            run(player, definition, TriggerRegistry.DAMAGED, placeholders);
        }
        runForDefensiveSlots(player, TriggerRegistry.DAMAGED, placeholders);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        String trigger = event.getEntity() instanceof Player
                ? TriggerRegistry.KILL_PLAYER
                : TriggerRegistry.KILL_ENTITY;
        Map<String, Object> placeholders = Map.of("target", event.getEntity().getName(), "damage", 0D);
        ProficiencyGuard.Session guard = plugin.proficiencyGuard()
                .session(killer, trigger, targetIdentity(event.getEntity()));
        ItemStack weapon = killWeapon(event.getEntity(), killer);
        EmakiItemDefinition definition = definition(weapon);
        if (definition != null
                && guard.admits(definition.id(), EquipmentSlotMatcher.SLOT_MAIN_HAND)
                && passes(killer, definition, trigger, weapon)) {
            run(killer, definition, trigger, placeholders, weapon);
        }
        runForArmorSlots(killer, trigger, placeholders, guard);
    }

    private ItemStack killWeapon(Entity victim, Player killer) {
        if (victim instanceof LivingEntity living
                && living.getLastDamageCause() instanceof EntityDamageByEntityEvent lastCause) {
            return damageSourceItem(killer, lastCause.getDamager());
        }
        return heldItem(killer);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EmakiItemDefinition definition = held(player);
        if (definition != null && passes(player, definition, TriggerRegistry.DEATH)) {
            run(player, definition, TriggerRegistry.DEATH, EMPTY_PLACEHOLDERS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBowSnapshot(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Projectile projectile) {
            snapshotProjectileSource(projectile, event.getBow());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack bow = event.getBow() == null ? heldItem(player) : event.getBow();
            EmakiItemDefinition definition = definition(bow);
            if (definition != null && passes(player, definition, TriggerRegistry.SHOOT_BOW, bow)) {
                run(player, definition, TriggerRegistry.SHOOT_BOW,
                        Map.of("projectile_type", event.getProjectile().getType().name()), bow);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof Player shooter && !ProjectileSourceSnapshot.has(projectile)) {
            snapshotProjectileSource(projectile, heldItem(shooter));
        }
        ProjectileTriggerResolver.ProjectileTriggers triggers = ProjectileTriggerResolver.resolve(projectile);
        if (triggers == null || triggers.launchTrigger() == null) {
            return;
        }
        if (projectile.getShooter() instanceof Player player) {
            ItemStack launchItem = projectileSourceItem(projectile, player);
            EmakiItemDefinition definition = definition(launchItem);
            if (definition != null && passes(player, definition, triggers.launchTrigger(), launchItem)) {
                run(player, definition, triggers.launchTrigger(),
                        Map.of("projectile_type", projectile.getType().name()), launchItem);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        Projectile projectile = event.getEntity();
        ItemStack sourceItem = projectileSourceItem(projectile, player);
        EmakiItemDefinition definition = definition(sourceItem);
        if (definition == null) {
            return;
        }
        String hitTrigger = ProjectileTriggerResolver.hitOrLandTrigger(projectile, event.getHitEntity() != null);
        if (hitTrigger != null && passes(player, definition, hitTrigger, sourceItem)) {
            run(player, definition, hitTrigger,
                    Map.of("projectile_type", projectile.getType().name()), sourceItem);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, TriggerRegistry.BREAK_BLOCK)) {
            run(event.getPlayer(), definition, TriggerRegistry.BREAK_BLOCK, Map.of("block", event.getBlock().getType().name()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack placed = event.getItemInHand();
        EmakiItemDefinition definition = definition(placed);
        if (definition != null && passes(event.getPlayer(), definition, TriggerRegistry.PLACE_BLOCK, placed)) {
            run(event.getPlayer(), definition, TriggerRegistry.PLACE_BLOCK, Map.of("block", event.getBlock().getType().name()), placed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, TriggerRegistry.TELEPORT)) {
            run(event.getPlayer(), definition, TriggerRegistry.TELEPORT, Map.of());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        EmakiItemDefinition definition = held(event.getPlayer());
        if (definition != null && passes(event.getPlayer(), definition, TriggerRegistry.LOGIN)) {
            run(event.getPlayer(), definition, TriggerRegistry.LOGIN, Map.of());
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
        PersistentDataContainer pdc = held.getItemMeta().getPersistentDataContainer();
        Byte value = pdc.get(ItemPdcKeys.DISABLED, PersistentDataType.BYTE);
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

    private ItemStack damageSourceItem(Player attacker, Entity damager) {
        if (damager instanceof Projectile projectile) {
            return projectileSourceItem(projectile, attacker);
        }
        return heldItem(attacker);
    }

    private ItemStack projectileSourceItem(Projectile projectile, Player shooter) {
        ItemStack snapshot = ProjectileSourceSnapshot.read(projectile);
        return snapshot == null ? heldItem(shooter) : snapshot;
    }

    private void snapshotProjectileSource(Projectile projectile, ItemStack launchItem) {
        if (definition(launchItem) == null) {
            return;
        }
        ProjectileSourceSnapshot.write(projectile, launchItem);
    }

    private void runForArmorSlots(Player player,
            String trigger,
            Map<String, Object> placeholders,
            ProficiencyGuard.Session guard) {
        runForEquippedSlots(player, trigger, placeholders, HAND_SLOTS, guard);
    }

    private void runForDefensiveSlots(Player player, String trigger, Map<String, Object> placeholders) {
        runForEquippedSlots(player, trigger, placeholders, WEAPON_SLOT, null);
    }

    private void runForEquippedSlots(Player player,
            String trigger,
            Map<String, Object> placeholders,
            Set<String> skippedSlots,
            ProficiencyGuard.Session guard) {
        if (player == null) {
            return;
        }
        for (EquipmentSourceResolver.EquipmentSource source : EquipmentSourceResolver.resolve(player)) {
            if (skippedSlots.contains(source.slotName())) {
                continue;
            }
            EmakiItemDefinition definition = definition(source.itemStack());
            if (definition == null || !matchesDeclaredSlot(source.slotName(), definition.equipSlot())) {
                continue;
            }
            if (guard != null && !guard.admits(definition.id(), source.slotName())) {
                continue;
            }
            if (!passes(player, definition, trigger, source.itemStack())) {
                continue;
            }
            run(player, definition, trigger, placeholders, source.itemStack());
        }
    }

    private boolean matchesDeclaredSlot(String actualSlot, String requiredSlot) {
        String normalizedRequired = EquipmentSlotMatcher.normalizeRequired(requiredSlot);
        return !EquipmentSlotMatcher.SLOT_ALL.equals(normalizedRequired)
                && EquipmentSlotMatcher.matches(actualSlot, normalizedRequired);
    }
}
