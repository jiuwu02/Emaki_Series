package emaki.jiuwu.craft.corelib.script.js.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptEventRegistry implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final List<JavaScriptEventSubscription> subscriptions = new ArrayList<>();

    public JavaScriptEventRegistry(Plugin plugin, JavaScriptService javaScriptService, ScriptConfig scriptConfig) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
    }

    public boolean register(JavaScriptEventSubscription subscription) {
        if (subscription == null || Texts.isBlank(subscription.id()) || !isSupported(subscription.eventType())) {
            return false;
        }
        subscriptions.add(subscription);
        return true;
    }

    public void unregister(String id) {
        String normalized = Texts.normalizeId(id);
        subscriptions.removeIf(subscription -> subscription.id().equals(normalized));
    }

    public List<JavaScriptEventSubscription> subscriptions() {
        return List.copyOf(subscriptions);
    }

    @Override
    public void close() {
        subscriptions.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteractLowest(PlayerInteractEvent event) {
        dispatch("player_interact", EventPriority.LOWEST, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteractLow(PlayerInteractEvent event) {
        dispatch("player_interact", EventPriority.LOW, event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerInteractNormal(PlayerInteractEvent event) {
        dispatch("player_interact", EventPriority.NORMAL, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteractHigh(PlayerInteractEvent event) {
        dispatch("player_interact", EventPriority.HIGH, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractHighest(PlayerInteractEvent event) {
        dispatch("player_interact", EventPriority.HIGHEST, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerInteractMonitor(PlayerInteractEvent event) {
        dispatch("player_interact", EventPriority.MONITOR, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerJoinLowest(PlayerJoinEvent event) {
        dispatch("player_join", EventPriority.LOWEST, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerJoinLow(PlayerJoinEvent event) {
        dispatch("player_join", EventPriority.LOW, event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerJoinNormal(PlayerJoinEvent event) {
        dispatch("player_join", EventPriority.NORMAL, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerJoinHigh(PlayerJoinEvent event) {
        dispatch("player_join", EventPriority.HIGH, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerJoinHighest(PlayerJoinEvent event) {
        dispatch("player_join", EventPriority.HIGHEST, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerJoinMonitor(PlayerJoinEvent event) {
        dispatch("player_join", EventPriority.MONITOR, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamageByEntityLowest(EntityDamageByEntityEvent event) {
        dispatch("entity_damage_by_entity", EventPriority.LOWEST, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onEntityDamageByEntityLow(EntityDamageByEntityEvent event) {
        dispatch("entity_damage_by_entity", EventPriority.LOW, event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onEntityDamageByEntityNormal(EntityDamageByEntityEvent event) {
        dispatch("entity_damage_by_entity", EventPriority.NORMAL, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityDamageByEntityHigh(EntityDamageByEntityEvent event) {
        dispatch("entity_damage_by_entity", EventPriority.HIGH, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByEntityHighest(EntityDamageByEntityEvent event) {
        dispatch("entity_damage_by_entity", EventPriority.HIGHEST, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityDamageByEntityMonitor(EntityDamageByEntityEvent event) {
        dispatch("entity_damage_by_entity", EventPriority.MONITOR, event);
    }

    private void dispatch(String eventType, EventPriority priority, org.bukkit.event.Event event) {
        if (javaScriptService == null || !javaScriptService.enabled() || subscriptions.isEmpty()) {
            return;
        }
        for (JavaScriptEventSubscription subscription : List.copyOf(subscriptions)) {
            if (!subscription.eventType().equals(eventType) || subscription.priority() != priority) {
                continue;
            }
            if (subscription.ignoreCancelled() && event instanceof org.bukkit.event.Cancellable cancellable && cancellable.isCancelled()) {
                continue;
            }
            invoke(subscription, event);
        }
    }

    private void invoke(JavaScriptEventSubscription subscription, org.bukkit.event.Event event) {
        try {
            boolean allowMutation = subscription.priority() != EventPriority.MONITOR;
            ScriptEventApi eventApi = new ScriptEventApi(subscription.eventType(), event, allowMutation);
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    subscription.scriptPath(),
                    subscription.functionName(),
                    List.of(eventApi, eventArguments(subscription)),
                    Map.of("extension", "event", "event", subscription.eventType(), "id", subscription.id()),
                    scriptConfig.clampTimeoutMillis(subscription.timeoutMillis()),
                    true
            ));
            if (result != null && !result.success()) {
                plugin.getLogger().warning("JavaScript event listener failed: " + subscription.id() + " -> " + result.message());
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("JavaScript event listener exception: " + subscription.id() + " -> " + exception.getMessage());
        }
    }

    private Map<String, Object> eventArguments(JavaScriptEventSubscription subscription) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("id", subscription.id());
        arguments.put("event", subscription.eventType());
        arguments.put("priority", subscription.priority().name());
        arguments.put("script", subscription.scriptPath());
        return arguments;
    }

    public static boolean isSupported(String eventType) {
        return switch (Texts.normalizeId(eventType)) {
            case "player_interact", "player_join", "entity_damage_by_entity" -> true;
            default -> false;
        };
    }
}
