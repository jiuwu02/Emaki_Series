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

            /**
             * Deliberately declared <em>without</em> {@code ignoreCancelled}.
             *
             * <p>{@link PlayerInteractEvent#isCancelled()} is defined as
             * {@code useInteractedBlock() == DENY}, and the constructor sets
             * {@code useClickedBlock = DENY} whenever there is no clicked
             * block. Every air click therefore arrives already "cancelled", so
             * {@code ignoreCancelled = true} would permanently suppress
             * {@code left_click} / {@code right_click} /
             * {@code shift_left_click} / {@code shift_right_click}.
             *
             * <p>Cancellation is instead evaluated through
             * {@link #externallySuppressed(PlayerInteractEvent)}, which reads
             * the item-in-hand result: that is the half of the event's two
             * cancellation states describing the item use the skill is cast
             * from, and it is {@code DEFAULT} rather than {@code DENY} for a
             * plain air click.
             */
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

    /**
     * Whether another plugin has denied the item use this trigger is cast from.
     *
     * <p>{@code setCancelled(true)} drives {@code useItemInHand} to
     * {@link Event.Result#DENY}, so region protection such as WorldGuard is
     * honoured, while an ordinary air click (which reports
     * {@code isCancelled() == true} purely as a vanilla no-op prediction) is
     * not mistaken for a cancellation.
     */
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
