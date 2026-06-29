package emaki.jiuwu.craft.corelib.api.script;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

public final class ScriptLoggerApi {

    private final Plugin plugin;
    private final String scriptPath;

    public ScriptLoggerApi(Plugin plugin, String scriptPath) {
        this.plugin = plugin;
        this.scriptPath = scriptPath == null ? "" : scriptPath;
    }

    @HostAccess.Export
    public void info(String message) {
        if (plugin != null) {
            plugin.getLogger().info(prefix() + message);
        }
    }

    @HostAccess.Export
    public void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning(prefix() + message);
        }
    }

    @HostAccess.Export
    public void error(String message) {
        if (plugin != null) {
            plugin.getLogger().severe(prefix() + message);
        }
    }

    private String prefix() {
        return "[Script " + scriptPath + "] ";
    }
}
