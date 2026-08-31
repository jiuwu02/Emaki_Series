package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;

public record FishGameplayEvent(Player player, String state)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "player_fish";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of("fish_state", state);
    }
}
