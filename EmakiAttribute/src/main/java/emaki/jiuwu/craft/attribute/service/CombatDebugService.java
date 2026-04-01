package emaki.jiuwu.craft.attribute.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;

public final class CombatDebugService {

    private final EmakiAttributePlugin plugin;
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();

    CombatDebugService(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (trackedPlayers.remove(playerId)) {
            return false;
        }
        trackedPlayers.add(playerId);
        return true;
    }

    public boolean setEnabled(Player player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (enabled) {
            trackedPlayers.add(player.getUniqueId());
            return true;
        }
        trackedPlayers.remove(player.getUniqueId());
        return false;
    }

    public boolean isEnabled(Player player) {
        return player != null && trackedPlayers.contains(player.getUniqueId());
    }

    public boolean isEnabled(Entity entity) {
        return entity instanceof Player player && isEnabled(player);
    }

    public boolean shouldTrace(LivingEntity attacker, LivingEntity target) {
        return isEnabled(attacker) || isEnabled(target);
    }

    public boolean shouldTrace(Projectile projectile, LivingEntity target) {
        Entity shooter = projectile == null ? null : projectile.getShooter() instanceof Entity entity ? entity : null;
        return shouldTrace(shooter instanceof LivingEntity livingEntity ? livingEntity : null, target);
    }

    public void log(String phase, String message) {
        if (plugin == null) {
            return;
        }
        String safePhase = phase == null || phase.isBlank() ? "TRACE" : phase;
        String safeMessage = message == null ? "" : message;
        plugin.getLogger().info("[CombatDebug][" + safePhase + "] " + safeMessage);
    }
}
