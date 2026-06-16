package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.Plugin;

public final class WebPluginApiRegistry {

    private static final Map<String, RegisteredRoute> ROUTES = new LinkedHashMap<>();

    private WebPluginApiRegistry() {
    }

    public static synchronized void register(Plugin plugin, String moduleId, String routeId, WebPluginApiHandler handler) {
        if (plugin == null || handler == null) {
            return;
        }
        String key = key(moduleId, routeId);
        if (key.isBlank()) {
            return;
        }
        ROUTES.put(key, new RegisteredRoute(plugin, handler));
    }

    public static synchronized void unregister(Plugin plugin) {
        if (plugin == null) {
            return;
        }
        ROUTES.entrySet().removeIf(entry -> entry.getValue().plugin() == plugin);
    }

    static synchronized RegisteredRoute route(String moduleId, String routeId) {
        return ROUTES.get(key(moduleId, routeId));
    }

    private static String key(String moduleId, String routeId) {
        String module = normalize(moduleId);
        String route = normalize(routeId);
        return module.isBlank() || route.isBlank() ? "" : module + "/" + route;
    }

    private static String normalize(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .replace('\\', '/')
                .replaceAll("^/+|/+$", "")
                .toLowerCase(Locale.ROOT);
    }

    static record RegisteredRoute(Plugin plugin, WebPluginApiHandler handler) {}

    @FunctionalInterface
    public interface WebPluginApiHandler {
        Map<String, ?> handle(WebPluginApiRequest request) throws IOException;
    }
}
