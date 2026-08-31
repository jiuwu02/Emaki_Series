package emaki.jiuwu.craft.corelib.metrics;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;

public final class BStatsService {

    private final JavaPlugin owner;
    private final Logger logger;
    private final LogMessages messages;
    private final Map<JavaPlugin, BStatsRegistration> registrations = new ConcurrentHashMap<>();

    public BStatsService(JavaPlugin owner, LogMessages messages) {
        this.owner = owner;
        this.logger = owner.getLogger();
        this.messages = messages;
    }

    public BStatsRegistration register(JavaPlugin plugin, int pluginId) {
        if (plugin == null || pluginId <= 0) {
            return BStatsRegistration.noop(plugin, pluginId);
        }
        BStatsRegistration previous = registrations.remove(plugin);
        if (previous != null) {
            previous.close();
        }
        try {
            Object metrics = createMetrics(plugin, pluginId);
            Method shutdownMethod = metrics.getClass().getMethod("shutdown");
            BStatsRegistration registration = BStatsRegistration.active(plugin, pluginId, () -> {
                try {
                    shutdownMethod.invoke(metrics);
                    info("console.bstats_closed", replacements(plugin, pluginId, null));
                } catch (Throwable throwable) {
                    fine("console.bstats_close_failed", replacements(plugin, pluginId, throwable), throwable);
                } finally {
                    registrations.remove(plugin);
                }
            });
            registrations.put(plugin, registration);
            info("console.bstats_registered", replacements(plugin, pluginId, null));
            return registration;
        } catch (Throwable throwable) {
            warning("console.bstats_register_failed", replacements(plugin, pluginId, throwable));
            fine("console.bstats_register_failed", replacements(plugin, pluginId, throwable), throwable);
            return BStatsRegistration.noop(plugin, pluginId);
        }
    }

    public void shutdownAll() {
        for (BStatsRegistration registration : List.copyOf(registrations.values())) {
            registration.close();
        }
        registrations.clear();
    }

    private Object createMetrics(JavaPlugin plugin, int pluginId) throws ReflectiveOperationException {
        ClassLoader classLoader = owner.getClass().getClassLoader();
        Class<?> metricsType = Class.forName("emaki.jiuwu.craft.runtime.bstats.bukkit.Metrics", true, classLoader);
        Constructor<?> constructor = metricsConstructor(metricsType);
        return constructor.newInstance(plugin, pluginId);
    }

    private Constructor<?> metricsConstructor(Class<?> metricsType) throws NoSuchMethodException {
        try {
            return metricsType.getConstructor(Plugin.class, int.class);
        } catch (NoSuchMethodException ignored) {
            return metricsType.getConstructor(JavaPlugin.class, int.class);
        }
    }

    private Map<String, String> replacements(JavaPlugin plugin, int pluginId, Throwable throwable) {
        return Map.of(
                "plugin", plugin == null ? "unknown" : plugin.getName(),
                "plugin_id", String.valueOf(pluginId),
                "error", errorMessage(throwable)
        );
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private void info(String key, Map<String, ?> replacements) {
        if (messages == null) {
            logger.info(key);
            return;
        }
        messages.info(key, replacements);
    }

    private void warning(String key, Map<String, ?> replacements) {
        if (messages == null) {
            logger.warning(key);
            return;
        }
        messages.warning(key, replacements);
    }

    private void fine(String key, Map<String, ?> replacements, Throwable throwable) {
        String message = messages == null ? key : messages.message(key, replacements);
        String plainMessage = MiniMessages.plain(MiniMessages.parse(message));
        if (throwable == null) {
            logger.fine(plainMessage);
            return;
        }
        logger.log(Level.FINE, plainMessage, throwable);
    }
}
