package emaki.jiuwu.craft.corelib.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class EmakiLog {

    private static final Map<String, Prefixed> LOGGERS = new ConcurrentHashMap<>();

    private EmakiLog() {
    }

    public static Logger of(Plugin plugin) {
        return of(plugin.getName());
    }

    public static Logger of(String pluginName) {
        return LOGGERS.computeIfAbsent(pluginName, EmakiLog::create);
    }

    public static void registerPrefix(String pluginName, Supplier<String> prefixSupplier) {
        if (pluginName == null || prefixSupplier == null) {
            return;
        }
        LOGGERS.computeIfAbsent(pluginName, EmakiLog::create).prefixSupplier = prefixSupplier;
    }

    private static Prefixed create(String pluginName) {
        Supplier<String> fallback = () -> "[" + pluginName + " ] ";
        String name = Bukkit.getServer() == null ? "emaki" : Bukkit.getLogger().getName();
        return new Prefixed(name, fallback);
    }

    private static final class Prefixed extends Logger {

        private volatile Supplier<String> prefixSupplier;

        private Prefixed(String name, Supplier<String> prefixSupplier) {
            super(name, null);
            this.prefixSupplier = prefixSupplier;
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
            String prefix = prefixSupplier.get();
            record.setMessage((prefix == null ? "" : prefix) + record.getMessage());
            if (getParent() == null && Bukkit.getServer() != null) {
                setParent(Bukkit.getLogger());
            }
            super.log(record);
        }
    }
}
