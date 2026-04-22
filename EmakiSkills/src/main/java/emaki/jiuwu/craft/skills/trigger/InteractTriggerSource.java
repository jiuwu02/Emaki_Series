package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Trigger source that converts {@link PlayerInteractEvent} into
 * left_click / right_click / shift_left_click / shift_right_click invocations.
 */
public final class InteractTriggerSource implements SkillTriggerSource {

    @Override
    public String id() {
        return "interact";
    }

    @Override
    public void register(JavaPlugin plugin, TriggerDispatcher dispatcher) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.NORMAL)
            public void onInteract(PlayerInteractEvent event) {
                Player player = event.getPlayer();
                boolean sneaking = player.isSneaking();

                String triggerId = switch (event.getAction()) {
                    case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK ->
                            sneaking ? "shift_left_click" : "left_click";
                    case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK ->
                            sneaking ? "shift_right_click" : "right_click";
                    default -> null;
                };

                if (triggerId == null) {
                    return;
                }

                TriggerInvocation invocation = new TriggerInvocation(
                        player,
                        triggerId,
                        event,
                        sneaking,
                        false,
                        System.currentTimeMillis()
                );
                dispatcher.dispatch(invocation);

                if (invocation.cancelOriginalAction()) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
    }
}
