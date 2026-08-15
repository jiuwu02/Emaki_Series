package emaki.jiuwu.craft.skills.trigger;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.skills.config.AppConfig;

public final class InteractTriggerSource implements SkillTriggerSource {

    private volatile boolean legacyDeprecationWarningLogged;

    @Override
    public String id() {
        return "interact";
    }

    @Override
    public void register(JavaPlugin plugin, TriggerDispatcher dispatcher) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.NORMAL)
            public void onInteract(PlayerInteractEvent event) {
                if (legacyDispatchCancelledEvents(plugin)) {
                    warnLegacyDeprecatedOnce(plugin);
                    dispatch(event);
                    return;
                }
                if (legacyDeprecationWarningLogged) {
                    legacyDeprecationWarningLogged = false;
                }
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

    private boolean legacyDispatchCancelledEvents(JavaPlugin plugin) {
        if (plugin instanceof AbstractConfigurableEmakiPlugin<?> configurable
                && configurable.appConfig() instanceof AppConfig appConfig) {
            return appConfig.triggerSettings().legacyDispatchCancelledEvents();
        }
        return false;
    }

    private void warnLegacyDeprecatedOnce(JavaPlugin plugin) {
        if (legacyDeprecationWarningLogged) {
            return;
        }
        legacyDeprecationWarningLogged = true;
        if (plugin instanceof LogMessagesProvider provider) {
            LogMessages messages = provider.messageService();
            if (messages != null) {
                messages.warning("console.trigger_legacy_cancelled_dispatch_deprecated",
                        Map.of("source", id()));
            }
        }
    }
}
