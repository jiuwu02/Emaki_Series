package emaki.jiuwu.craft.corelib.script.api;

import org.bukkit.plugin.Plugin;

public final class ScriptLoggerApi {

    private final Plugin plugin;
    private final String scriptPath;

    public ScriptLoggerApi(Plugin plugin, String scriptPath) {
        this.plugin = plugin;
        this.scriptPath = scriptPath == null ? "" : scriptPath;
    }

    public void info(String message) {
        if (plugin != null) {
            plugin.getLogger().info(prefix() + message);
        }
    }

    public void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning(prefix() + message);
        }
    }

    public void error(String message) {
        if (plugin != null) {
            plugin.getLogger().severe(prefix() + message);
        }
    }

    private String prefix() {
        return "[Script " + scriptPath + "] ";
    }
}
