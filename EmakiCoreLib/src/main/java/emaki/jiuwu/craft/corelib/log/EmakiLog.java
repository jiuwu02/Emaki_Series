package emaki.jiuwu.craft.corelib.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class EmakiLog {

    private static final Map<String, Logger> LOGGERS = new ConcurrentHashMap<>();

    private EmakiLog() {
    }

    public static Logger of(Plugin plugin) {
        return of(plugin.getName());
    }

    public static Logger of(String pluginName) {
        return LOGGERS.computeIfAbsent(pluginName, EmakiLog::create);
    }

    private static Logger create(String pluginName) {
        String prefix = "[" + pluginName + "] ";
        if (Bukkit.getServer() == null) {
            return new Prefixed("emaki", prefix);
        }
        return new Prefixed(Bukkit.getLogger().getName(), prefix);
    }

    private static final class Prefixed extends Logger {

        private final String prefix;

        private Prefixed(String name, String prefix) {
            super(name, null);
            this.prefix = prefix;
            if (Bukkit.getServer() != null) {
                setParent(Bukkit.getLogger());
            }
            setUseParentHandlers(true);
        }

        @Override
        public String getName() {
            return Bukkit.getServer() == null ? super.getName() : Bukkit.getLogger().getName();
        }

        @Override
        public void log(LogRecord record) {
            record.setMessage(prefix + record.getMessage());
            if (getParent() == null && Bukkit.getServer() != null) {
                setParent(Bukkit.getLogger());
            }
            super.log(record);
        }
    }
}
