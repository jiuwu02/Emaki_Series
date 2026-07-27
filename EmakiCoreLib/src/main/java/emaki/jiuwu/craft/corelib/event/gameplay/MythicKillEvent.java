package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;











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
