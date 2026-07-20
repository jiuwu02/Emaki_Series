package emaki.jiuwu.craft.corelib.script.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.js.event.JavaScriptEventSubscription;
import emaki.jiuwu.craft.corelib.script.js.event.ScriptEventApi;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;





public final class JavaScriptEventRegistry implements Listener, AutoCloseable {

    private static final Map<String, Class<? extends Event>> SUPPORTED_EVENTS = supportedEvents();

    private final Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final MessageService messageService;
    private final ScriptConfig scriptConfig;
    private final List<JavaScriptEventSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Map<String, Listener> listenersById = new ConcurrentHashMap<>();

    public JavaScriptEventRegistry(Plugin plugin,
            JavaScriptService javaScriptService,
            MessageService messageService,
            ScriptConfig scriptConfig) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.messageService = messageService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
    }

    public static boolean isSupported(String eventType) {
        return resolveEventClass(eventType) != null;
    }

    public boolean register(JavaScriptEventSubscription subscription) {
        if (subscription == null || plugin == null || Texts.isBlank(subscription.id())) {
            return false;
        }
        Class<? extends Event> eventClass = resolveEventClass(subscription.eventType());
        if (eventClass == null) {
            return false;
        }
        unregister(subscription.id());
        Listener listener = new Listener() {
        };
        EventExecutor executor = (_, event) -> dispatch(subscription, event);
        try {
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    subscription.priority(),
                    executor,
                    plugin,
                    subscription.ignoreCancelled()
            );
        } catch (RuntimeException exception) {
            warning("console.js_event_unsupported", Map.of(
                    "event", Texts.toStringSafe(subscription.eventType()),
                    "script", Texts.toStringSafe(subscription.scriptPath())
            ));
            return false;
        }
        subscriptions.add(subscription);
        listenersById.put(Texts.normalizeId(subscription.id()), listener);
        return true;
    }

    public void register(String id,
            String eventType,
            String scriptPath,
            String functionName,
            long timeoutMillis) {
        register(new JavaScriptEventSubscription(
                Texts.normalizeId(id),
                eventType,
                EventPriority.NORMAL,
                true,
                scriptPath,
                functionName,
                timeoutMillis
        ));
    }

    public void unregister(String id) {
        String normalized = Texts.normalizeId(id);
        if (Texts.isBlank(normalized)) {
            return;
        }
        subscriptions.removeIf(subscription -> Texts.normalizeId(subscription.id()).equals(normalized));
        Listener listener = listenersById.remove(normalized);
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
    }

    public List<JavaScriptEventSubscription> subscriptions() {
        return List.copyOf(subscriptions);
    }

    public void clear() {
        for (Listener listener : List.copyOf(listenersById.values())) {
            HandlerList.unregisterAll(listener);
        }
        listenersById.clear();
        subscriptions.clear();
    }

    public int size() {
        return subscriptions.size();
    }

    private void dispatch(JavaScriptEventSubscription subscription, Event event) {
        if (event == null || event.isAsynchronous() || javaScriptService == null || !javaScriptService.enabled()) {
            return;
        }
        if (subscription.ignoreCancelled()
                && event instanceof Cancellable cancellable
                && cancellable.isCancelled()) {
            return;
        }
        boolean allowMutation = subscription.priority() != EventPriority.MONITOR;
        ScriptEventApi api = capture(subscription.eventType(), event, allowMutation);
        invokeSynchronously(subscription, api);
        if (allowMutation) {
            replay(event, api);
        }
    }

    private void invokeSynchronously(JavaScriptEventSubscription subscription, ScriptEventApi api) {
        try {
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    subscription.scriptPath(),
                    subscription.functionName(),
                    List.of(api, eventArguments(subscription)),
                    ScriptSnapshots.immutableMap(Map.of(
                            "extension", "event",
                            "event", subscription.eventType(),
                            "id", subscription.id()
                    )),
                    scriptConfig.clampTimeoutMillis(subscription.timeoutMillis()),
                    true
            ));
            if (result != null && !result.success()) {
                warning("console.js_event_listener_failed", Map.of(
                        "id", subscription.id(),
                        "error", Texts.toStringSafe(result.message())
                ));
            }
        } catch (RuntimeException exception) {
            warning("console.js_event_listener_exception", Map.of(
                    "id", subscription.id(),
                    "error", Texts.toStringSafe(exception.getMessage())
            ));
        }
    }

    private static ScriptEventApi capture(String eventType, Event event, boolean allowMutation) {
        Entity entity = eventEntity(event);
        Player player = eventPlayer(event);
        Entity damager = event instanceof EntityDamageByEntityEvent damageByEntity
                ? damageByEntity.getDamager()
                : null;
        boolean cancellable = event instanceof Cancellable;
        boolean cancelled = cancellable && ((Cancellable) event).isCancelled();
        boolean hasDamage = event instanceof EntityDamageEvent;
        double damage = hasDamage ? ((EntityDamageEvent) event).getDamage() : 0D;
        String cause = hasDamage ? ((EntityDamageEvent) event).getCause().name() : "";
        boolean hasMessage = event instanceof PlayerCommandPreprocessEvent
                || event instanceof ServerCommandEvent;
        String message = switch (event) {
            case PlayerCommandPreprocessEvent command -> command.getMessage();
            case ServerCommandEvent command -> command.getCommand();
            default -> "";
        };
        String command = message;
        PlayerMoveEvent move = event instanceof PlayerMoveEvent moveEvent ? moveEvent : null;
        return new ScriptEventApi(
                eventType,
                false,
                cancellable,
                cancelled,
                allowMutation,
                ScriptEntitySnapshot.capture(entity),
                ScriptEntitySnapshot.capture(player),
                ScriptEntitySnapshot.capture(damager),
                cause,
                hasDamage,
                damage,
                hasMessage,
                message,
                command,
                move == null ? Map.of() : location(move.getFrom()),
                move == null ? Map.of() : location(move.getTo()),
                move != null && move.getTo() != null
        );
    }

    private static void replay(Event event, ScriptEventApi api) {
        if (api.cancellationDirty() && event instanceof Cancellable cancellable) {
            cancellable.setCancelled(api.cancelled());
        }
        if (api.damageDirty() && event instanceof EntityDamageEvent damageEvent) {
            damageEvent.setDamage(api.damage());
        }
        if (api.messageDirty()) {
            switch (event) {
                case PlayerCommandPreprocessEvent command -> command.setMessage(api.message());
                case ServerCommandEvent command -> command.setCommand(api.message());
                default -> {
                }
            }
        }
        if (api.toDirty() && event instanceof PlayerMoveEvent moveEvent && moveEvent.getTo() != null) {
            Location moved = moveEvent.getTo().clone();
            moved.setX(number(api.to().get("x"), moved.getX()));
            moved.setY(number(api.to().get("y"), moved.getY()));
            moved.setZ(number(api.to().get("z"), moved.getZ()));
            moveEvent.setTo(moved);
        }
    }

    private Map<String, Object> eventArguments(JavaScriptEventSubscription subscription) {
        return ScriptSnapshots.immutableMap(Map.of(
                "id", subscription.id(),
                "event", subscription.eventType(),
                "priority", subscription.priority().name(),
                "script", subscription.scriptPath()
        ));
    }

    private static Entity eventEntity(Event event) {
        if (event instanceof EntityEvent entityEvent) {
            return entityEvent.getEntity();
        }
        if (event instanceof BlockBreakEvent blockBreakEvent) {
            return blockBreakEvent.getPlayer();
        }
        if (event instanceof InventoryClickEvent inventoryClickEvent) {
            return inventoryClickEvent.getWhoClicked();
        }
        return null;
    }

    private static Player eventPlayer(Event event) {
        if (event instanceof PlayerEvent playerEvent) {
            return playerEvent.getPlayer();
        }
        if (event instanceof BlockBreakEvent blockBreakEvent) {
            return blockBreakEvent.getPlayer();
        }
        if (event instanceof InventoryClickEvent inventoryClickEvent
                && inventoryClickEvent.getWhoClicked() instanceof Player player) {
            return player;
        }
        if (event instanceof EntityDeathEvent deathEvent
                && deathEvent.getEntity() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static Map<String, Object> location(Location location) {
        if (location == null) {
            return Map.of();
        }
        return ScriptSnapshots.immutableMap(Map.of(
                "world", location.getWorld() == null ? "" : location.getWorld().getName(),
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ(),
                "yaw", location.getYaw(),
                "pitch", location.getPitch()
        ));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Class<? extends Event> resolveEventClass(String eventType) {
        if (Texts.isBlank(eventType)) {
            return null;
        }
        Class<? extends Event> supported = SUPPORTED_EVENTS.get(normalizeType(eventType));
        if (supported != null) {
            return supported;
        }
        if (!eventType.contains(".")) {
            return null;
        }
        try {
            Class<?> rawClass = Class.forName(eventType);
            return Event.class.isAssignableFrom(rawClass) ? rawClass.asSubclass(Event.class) : null;
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    private static Map<String, Class<? extends Event>> supportedEvents() {
        Map<String, Class<? extends Event>> events = new LinkedHashMap<>();
        addEvent(events, PlayerInteractEvent.class);
        addEvent(events, PlayerJoinEvent.class);
        addEvent(events, PlayerQuitEvent.class);
        addEvent(events, PlayerCommandPreprocessEvent.class);
        addEvent(events, PlayerMoveEvent.class);
        addEvent(events, BlockBreakEvent.class);
        addEvent(events, InventoryClickEvent.class);
        addEvent(events, EntityDamageEvent.class);
        addEvent(events, EntityDamageByEntityEvent.class);
        addEvent(events, EntityDeathEvent.class);
        addEvent(events, ServerCommandEvent.class);
        return Map.copyOf(events);
    }

    private static void addEvent(Map<String, Class<? extends Event>> events,
            Class<? extends Event> eventClass) {
        events.put(normalizeType(eventClass.getSimpleName()), eventClass);
    }

    private static String normalizeType(String type) {
        String normalized = Texts.trim(type).replace('-', '_');
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isUpperCase(current) && index > 0 && normalized.charAt(index - 1) != '_') {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(current));
        }
        String result = builder.toString().toLowerCase(Locale.ROOT);
        if (result.endsWith("_event")) {
            result = result.substring(0, result.length() - "_event".length());
        }
        return result.replaceAll("__+", "_");
    }

    private void warning(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            messageService.warning(key, replacements);
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        clear();
    }
}
