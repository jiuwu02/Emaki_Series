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

public final class DropTriggerSource implements SkillTriggerSource {

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

            /**
             * Legacy compatibility path, only active while
             * {@code trigger_settings.legacy_dispatch_cancelled_events} is
             * {@code true}. It deliberately omits {@code ignoreCancelled} so
             * that events already cancelled by protection plugins still reach
             * the dispatcher, reproducing the pre-fix behaviour.
             *
             * <p>Temporary: kept for one full minor cycle, removed in the next
             * major release.
             */
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
