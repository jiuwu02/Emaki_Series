package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Fired when a living entity dies and the kill can be attributed to a player.
 *
 * <p>{@code directKill} distinguishes a kill credited by Bukkit's own
 * {@link LivingEntity#getKiller()} (melee or otherwise registered killer) from one recovered
 * through the publisher's last-damager tracking (projectiles, delayed deaths). Subscribers that
 * only want vanilla-attributed kills can filter on it, preserving pre-existing behavior.
 *
 * @param player     the attributed killer
 * @param victim     the entity that died
 * @param directKill whether the killer came from {@link LivingEntity#getKiller()} directly
 */
public record EntityKillEvent(Player player, LivingEntity victim, boolean directKill)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "entity_kill";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of("entity_type", victim.getType().name());
    }
}
