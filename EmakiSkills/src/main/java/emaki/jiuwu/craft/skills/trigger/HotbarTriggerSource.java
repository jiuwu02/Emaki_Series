package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Trigger source that converts {@link PlayerItemHeldEvent} into
 * hotbar_1 through hotbar_9 invocations.
 */
public final class HotbarTriggerSource implements SkillTriggerSource {

    @Override
    public String id() {
        return "hotbar";
    }

    @Override
    public void register(JavaPlugin plugin, TriggerDispatcher dispatcher) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.NORMAL)
            public void onHotbar(PlayerItemHeldEvent event) {
                Player player = event.getPlayer();
                String triggerId = "hotbar_" + (event.getNewSlot() + 1);

                TriggerInvocation invocation = new TriggerInvocation(
                        player,
                        triggerId,
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
