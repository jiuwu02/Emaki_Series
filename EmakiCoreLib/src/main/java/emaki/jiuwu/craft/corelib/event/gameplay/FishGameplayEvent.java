package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;

/**
 * Fired on any {@link org.bukkit.event.player.PlayerFishEvent}. {@code state} is the fishing
 * state name (e.g. {@code CAUGHT_FISH}, {@code FISHING}); subscribers decide which states matter.
 *
 * @param player the fishing player
 * @param state  the {@code PlayerFishEvent.State} name
 */
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
