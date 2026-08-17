package emaki.jiuwu.craft.skills.trigger;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.skills.config.AppConfig;

import emaki.jiuwu.craft.corelib.trigger.TriggerDispatcher;
import emaki.jiuwu.craft.corelib.trigger.TriggerInvocation;
import emaki.jiuwu.craft.corelib.trigger.TriggerSource;

public final class DropTriggerSource implements TriggerSource {

    private volatile boolean legacyDeprecationWarningLogged;

    @Override
    public String id() {
        return "drop";
    }

    @Override
    public void register(JavaPlugin plugin, TriggerDispatcher dispatcher) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
            public void onDrop(PlayerDropItemEvent event) {
                if (legacyDispatchCancelledEvents(plugin)) {
                    return;
                }
                if (legacyDeprecationWarningLogged) {
                    legacyDeprecationWarningLogged = false;
                }
                dispatch(event);
            }

            @EventHandler(priority = EventPriority.NORMAL)
            public void onDropLegacy(PlayerDropItemEvent event) {
                if (!legacyDispatchCancelledEvents(plugin)) {
                    return;
                }
                warnLegacyDeprecatedOnce(plugin);
                dispatch(event);
            }

            private void dispatch(PlayerDropItemEvent event) {
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
