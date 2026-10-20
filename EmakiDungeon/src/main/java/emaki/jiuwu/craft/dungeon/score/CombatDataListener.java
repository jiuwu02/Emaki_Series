package emaki.jiuwu.craft.dungeon.score;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;

import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class CombatDataListener implements Listener {

    private final InstanceRegistry registry;
    private final ScoreTracker tracker;

    public CombatDataListener(InstanceRegistry registry, ScoreTracker tracker) {
        this.registry = registry;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        DungeonInstance instance = registry.get(target.getWorld().getName());
        if (instance == null) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null && !(target instanceof Player)) {
            tracker.add(instance.instanceId(), attacker.getUniqueId(),
                    ScoreTracker.Stat.DAMAGE, event.getFinalDamage());
            return;
        }
        if (target instanceof Player defender) {
            tracker.add(instance.instanceId(), defender.getUniqueId(),
                    ScoreTracker.Stat.DEFENSE, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        DungeonInstance instance = registry.get(event.getEntity().getWorld().getName());
        if (instance == null) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            tracker.add(instance.instanceId(), killer.getUniqueId(),
                    ScoreTracker.Stat.KILLS, 1.0D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonInstance instance = registry.get(player.getWorld().getName());
        if (instance == null) {
            return;
        }
        tracker.add(instance.instanceId(), player.getUniqueId(), ScoreTracker.Stat.DEATH, 1.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof Player thrower && hasHealEffect(event)) {
            DungeonInstance instance = registry.get(thrower.getWorld().getName());
            if (instance == null) {
                return;
            }
            for (LivingEntity affected : event.getAffectedEntities()) {
                if (!(affected instanceof Player target)) {
                    continue;
                }
                var maxHealth = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                double missing = (maxHealth == null ? 20.0D : maxHealth.getValue()) - target.getHealth();
                if (missing <= 0.0D) {
                    continue;
                }
                tracker.add(instance.instanceId(), thrower.getUniqueId(),
                        ScoreTracker.Stat.HEAL, missing * event.getIntensity(target));
            }
        }
    }

    private static boolean hasHealEffect(PotionSplashEvent event) {
        return event.getEntity().getEffects().stream()
                .anyMatch(effect -> effect.getType().equals(PotionEffectType.INSTANT_HEALTH));
    }

    private static Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
