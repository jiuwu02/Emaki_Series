package emaki.jiuwu.craft.level.listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

public final class CombatSourceListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;
    private final Map<UUID, DamageAttribution> lastDamagers = new ConcurrentHashMap<>();

    public CombatSourceListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.appConfig().lastDamagerTracking() || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player player = playerDamager(event.getDamager());
        if (player != null) {
            lastDamagers.put(event.getEntity().getUniqueId(), new DamageAttribution(player.getUniqueId(), System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            DamageAttribution attribution = lastDamagers.get(entity.getUniqueId());
            if (attribution != null && System.currentTimeMillis() - attribution.time() <= plugin.appConfig().lastDamagerExpireTicks() * 50L) {
                killer = plugin.getServer().getPlayer(attribution.playerId());
            }
        }
        lastDamagers.remove(entity.getUniqueId());
        if (killer == null) {
            return;
        }
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("entity_kill")) {
            if (!source.includePlayers() && entity instanceof Player) {
                continue;
            }
            SourceRuleConfig.Rule rule = sourceService.matchEntity(source, entity.getType());
            if (rule == null) {
                continue;
            }
            sourceService.award(killer, source, rule, Map.of("entity_type", entity.getType().name()), "entity_kill");
        }
        awardMythic(killer, entity);
    }

    private void awardMythic(Player killer, LivingEntity entity) {
        if (!plugin.appConfig().mythicEnabled() || !plugin.appConfig().mythicKillSources() || !plugin.getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        MythicMobInfo info = mythicMobInfo(entity);
        if (info == null) {
            return;
        }
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("mythic_mob_kill")) {
            SourceRuleConfig.Rule rule = sourceService.matchMobId(source, info.mobId());
            if (rule != null) {
                sourceService.award(killer, source, rule, Map.of("mythic_id", info.mobId(), "mythic_level", info.level()), "mythic_mob_kill");
            }
        }
    }

    private MythicMobInfo mythicMobInfo(LivingEntity entity) {
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
                if (!(bukkitEntity instanceof Entity bukkit) || !bukkit.getUniqueId().equals(entity.getUniqueId())) {
                    continue;
                }
                return new MythicMobInfo(resolveMobId(activeMob), resolveMobLevel(activeMob));
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

    private Player playerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private record DamageAttribution(UUID playerId, long time) {
    }

    private record MythicMobInfo(String mobId, double level) {
    }
}
