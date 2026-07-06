package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Fired when a player tames an entity.
 *
 * @param player     the taming player
 * @param entityType the tamed entity's type
 */
public record TameGameplayEvent(Player player, EntityType entityType)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "entity_tame";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of("entity_type", entityType.name());
    }
}
