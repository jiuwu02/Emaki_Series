package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.trigger.TriggerDispatcher;
import emaki.jiuwu.craft.corelib.trigger.TriggerInvocation;
import emaki.jiuwu.craft.corelib.trigger.TriggerSource;

public final class InteractTriggerSource implements TriggerSource {

    @Override
    public String id() {
        return "interact";
    }

    @Override
    public void register(JavaPlugin plugin, TriggerDispatcher dispatcher) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.NORMAL)
            public void onInteract(PlayerInteractEvent event) {
                if (externallySuppressed(event)) {
                    return;
                }
                dispatch(event);
            }

            private void dispatch(PlayerInteractEvent event) {
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

    private boolean externallySuppressed(PlayerInteractEvent event) {
        return event.useItemInHand() == Event.Result.DENY;
    }
}
