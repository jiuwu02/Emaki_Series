package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.event.EmakiEvent;






















public interface GameplayEvent extends EmakiEvent {


    Player player();


    String triggerKey();


    Map<String, Object> variables();

    @Override
    default String eventType() {
        return triggerKey();
    }
}
