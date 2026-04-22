package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Trigger source that converts {@link PlayerDropItemEvent} into a drop_q invocation.
 */
public final class DropTriggerSource implements SkillTriggerSource {

    @Override
    public String id() {
        return "drop";
    }

    @Override
    public void register(JavaPlugin plugin, TriggerDispatcher dispatcher) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.NORMAL)
            public void onDrop(PlayerDropItemEvent event) {
                Player player = event.getPlayer();

                TriggerInvocation invocation = new TriggerInvocation(
                        player,
                        "drop_q",
                        event,
                        player.isSneaking(),
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
