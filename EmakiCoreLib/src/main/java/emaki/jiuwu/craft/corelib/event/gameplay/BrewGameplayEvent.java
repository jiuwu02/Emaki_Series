package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;

/**
 * Fired one tick after a brewing stand finishes, attributed to the last player who interacted
 * with the stand (opened or clicked its inventory).
 *
 * <p>{@code ageTicks} is how long ago that interaction happened. The publisher only attributes
 * within a coarse maximum window; subscribers can enforce their own, tighter per-rule
 * attribution windows by comparing against {@code ageTicks}.
 *
 * @param player     the attributed player
 * @param potionType the resulting base potion type name, or {@code UNKNOWN}
 * @param ageTicks   ticks elapsed since the attributed interaction
 */
public record BrewGameplayEvent(Player player, String potionType, long ageTicks)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "brew_complete";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of("potion_type", potionType);
    }
}
