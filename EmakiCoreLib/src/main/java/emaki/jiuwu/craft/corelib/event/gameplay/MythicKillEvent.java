package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Fired alongside {@link EntityKillEvent} when the victim is a MythicMobs mob. Carries the
 * resolved MythicMobs internal id and level so subscribers never repeat the reflection the
 * publisher already performed.
 *
 * @param player the attributed killer
 * @param victim the mob that died
 * @param mobId  the MythicMobs internal name
 * @param level  the mob level (defaults to {@code 1} when unavailable)
 */
public record MythicKillEvent(Player player, LivingEntity victim, String mobId, double level)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "mythic_mob_kill";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of("mythic_id", mobId, "mythic_level", level);
    }
}
