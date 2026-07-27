package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;













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
