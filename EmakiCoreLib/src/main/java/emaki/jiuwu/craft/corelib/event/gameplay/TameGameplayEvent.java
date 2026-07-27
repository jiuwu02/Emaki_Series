package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;







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
