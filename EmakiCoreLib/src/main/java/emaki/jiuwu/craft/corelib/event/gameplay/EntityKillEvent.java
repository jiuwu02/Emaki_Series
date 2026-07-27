package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;













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
