package emaki.jiuwu.craft.corelib.api.script;

import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

public final class ScriptLoggerApi {

    private final Logger logger;
    private final String scriptPath;

    public ScriptLoggerApi(Plugin plugin, String scriptPath) {
        this.logger = plugin == null ? null : plugin.getLogger();
        this.scriptPath = scriptPath == null ? "" : scriptPath;
    }

    @HostAccess.Export
    public void info(String message) {
        if (logger != null) {
            logger.info(prefix() + message);
        }
    }

    @HostAccess.Export
    public void warn(String message) {
        if (logger != null) {
            logger.warning(prefix() + message);
        }
    }

    @HostAccess.Export
    public void error(String message) {
        if (logger != null) {
            logger.severe(prefix() + message);
        }
    }

    private String prefix() {
        return "[Script " + scriptPath + "] ";
    }
}
