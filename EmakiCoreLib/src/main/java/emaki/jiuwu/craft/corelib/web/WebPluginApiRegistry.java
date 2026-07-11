package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
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
        String key = key(moduleId, routeId);
        RegisteredRoute route = ROUTES.get(key);
        if (route == null) {
            return null;
        }
        if (!isPluginCurrent(route.plugin())) {
            ROUTES.remove(key);
            return null;
        }
        return route.plugin().isEnabled() ? route : null;
    }

    public static synchronized List<Map<String, Object>> routesSnapshot() {
        ROUTES.entrySet().removeIf(entry -> !isPluginCurrent(entry.getValue().plugin()));
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Map.Entry<String, RegisteredRoute> entry : ROUTES.entrySet()) {
            String[] parts = entry.getKey().split("/", 2);
            Plugin plugin = entry.getValue().plugin();
            boolean enabled = plugin.isEnabled();
            Map<String, Object> route = new LinkedHashMap<>();
            route.put("moduleId", parts.length > 0 ? parts[0] : "");
            route.put("routeId", parts.length > 1 ? parts[1] : "");
            route.put("present", true);
            route.put("enabled", enabled);
            route.put("status", enabled ? "OK" : "DISABLED");
            routes.add(route);
        }
        return routes;
    }

    private static boolean isPluginCurrent(Plugin plugin) {
        if (plugin == null) {
            return false;
        }
        Plugin installed = Bukkit.getPluginManager().getPlugin(plugin.getName());
        return installed == plugin;
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
