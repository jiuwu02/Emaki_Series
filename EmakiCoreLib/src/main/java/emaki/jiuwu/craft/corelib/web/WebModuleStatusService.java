package emaki.jiuwu.craft.corelib.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

public final class WebModuleStatusService {

    private final List<String> configuredModules;

    public WebModuleStatusService(List<String> modules) {
        this.configuredModules = modules == null ? List.of() : List.copyOf(modules);
    }

    public List<Map<String, Object>> modules() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> modules = configuredModules.isEmpty() ? WebConsoleRegistry.registeredModuleIds() : configuredModules;
        for (String moduleId : modules) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(moduleId);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", moduleId);
            entry.put("present", plugin != null);
            entry.put("enabled", plugin != null && plugin.isEnabled());
            if (plugin != null) {
                PluginDescriptionFile description = plugin.getDescription();
                entry.put("version", description.getVersion());
                entry.put("main", description.getMain());
                entry.put("authors", description.getAuthors());
                entry.put("status", plugin.isEnabled() ? "OK" : "DISABLED");
            } else {
                entry.put("version", "");
                entry.put("main", "");
                entry.put("authors", List.of());
                entry.put("status", "MISSING");
            }
            result.add(entry);
        }
        return result;
    }
}
