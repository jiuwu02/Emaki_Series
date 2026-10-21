package emaki.jiuwu.craft.dummy.listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.dummy.log.DummyLogger;
import emaki.jiuwu.craft.dummy.log.LogLevel;
import emaki.jiuwu.craft.dummy.session.DummyManager;

public final class ServerEventBridge implements Listener {

    private static final String[] CANDIDATE_PACKAGES = {
            "org.bukkit.event.player.",
            "org.bukkit.event.block.",
            "org.bukkit.event.entity.",
            "org.bukkit.event.inventory.",
            "org.bukkit.event.world.",
            "org.bukkit.event.server.",
            "io.papermc.paper.event.player.",
            "com.destroystokyo.paper.event.player.",
            "emaki.jiuwu.craft.dungeon.api.event."
    };

    private final JavaPlugin plugin;
    private final DummyManager manager;
    private final DummyLogger logger;
    private final EventObserver observer;
    private final Map<String, Class<? extends Event>> watchedEvents = new ConcurrentHashMap<>();

    public ServerEventBridge(JavaPlugin plugin, DummyManager manager, DummyLogger logger,
            EventObserver observer) {
        this.plugin = plugin;
        this.manager = manager;
        this.logger = logger;
        this.observer = observer;
    }

    public interface EventObserver {
        void onObservedEvent(String type, String detail);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.find(player.getName()) == null) {
            return;
        }
        logger.log(LogLevel.EVENT, player.getName(), "bukkit_join uuid=" + player.getUniqueId());
        manager.markBukkitJoin(player.getName(), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.find(player.getName()) == null) {
            return;
        }
        logger.log(LogLevel.EVENT, player.getName(), "bukkit_quit uuid=" + player.getUniqueId());
        manager.markBukkitQuit(player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (manager.find(player.getName()) == null) {
            return;
        }
        logger.log(LogLevel.EVENT, player.getName(), "bukkit_command cmd=" + event.getMessage());
    }

    public Class<? extends Event> ensureWatching(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        Class<? extends Event> cached = watchedEvents.get(typeName);
        if (cached != null) {
            return cached;
        }
        Class<? extends Event> resolved = resolveEventClass(typeName);
        if (resolved == null) {
            return null;
        }
        Class<? extends Event> raced = watchedEvents.putIfAbsent(typeName, resolved);
        if (raced != null) {
            return raced;
        }
        EventExecutor executor = (listener, event) -> dispatchObserved(resolved, event);
        Bukkit.getPluginManager().registerEvent(resolved, this, EventPriority.MONITOR, executor, plugin);
        return resolved;
    }

    private void dispatchObserved(Class<? extends Event> eventType, Event event) {
        String detail = describe(event);
        logger.log(LogLevel.EVENT, "event", eventType.getSimpleName() + " " + detail);
        observer.onObservedEvent(eventType.getSimpleName(), detail);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> resolveEventClass(String typeName) {
        if (typeName.indexOf('.') >= 0) {
            try {
                return (Class<? extends Event>) Class.forName(typeName, false,
                        ServerEventBridge.class.getClassLoader());
            } catch (ClassNotFoundException | ClassCastException failure) {
                return null;
            }
        }
        for (String candidatePackage : CANDIDATE_PACKAGES) {
            try {
                return (Class<? extends Event>) Class.forName(candidatePackage + typeName, false,
                        ServerEventBridge.class.getClassLoader());
            } catch (ClassNotFoundException | ClassCastException failure) {
                continue;
            }
        }
        return null;
    }

    private String describe(Event event) {
        StringBuilder detail = new StringBuilder(96);
        Object player = invokeOptional(event, "getPlayer");
        if (player instanceof Player bukkitPlayer) {
            append(detail, "player", bukkitPlayer.getName());
        }
        Object message = invokeOptional(event, "getMessage");
        if (message != null) {
            append(detail, "cmd", String.valueOf(message));
        }
        Object instanceId = invokeOptional(event, "getInstanceId");
        if (instanceId != null) {
            append(detail, "instance", String.valueOf(instanceId));
        }
        Object dungeonId = invokeOptional(event, "getDungeonId");
        if (dungeonId != null) {
            append(detail, "dungeon", String.valueOf(dungeonId));
        }
        if (detail.length() == 0) {
            append(detail, "raw", String.valueOf(event));
        }
        return detail.toString().trim();
    }

    private static Object invokeOptional(Event event, String methodName) {
        try {
            return event.getClass().getMethod(methodName).invoke(event);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (ReflectiveOperationException failure) {
            return null;
        }
    }

    private static void append(StringBuilder detail, String key, String value) {
        if (detail.length() > 0) {
            detail.append(' ');
        }
        detail.append(key).append('=').append(value);
    }
}
