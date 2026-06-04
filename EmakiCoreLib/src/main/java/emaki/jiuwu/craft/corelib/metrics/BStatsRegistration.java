package emaki.jiuwu.craft.corelib.metrics;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.java.JavaPlugin;

public final class BStatsRegistration implements AutoCloseable {

    private final JavaPlugin plugin;
    private final int pluginId;
    private final boolean active;
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();

    static BStatsRegistration active(JavaPlugin plugin, int pluginId, Runnable closeAction) {
        return new BStatsRegistration(plugin, pluginId, true, closeAction);
    }

    public static BStatsRegistration noop(JavaPlugin plugin, int pluginId) {
        return new BStatsRegistration(plugin, pluginId, false, () -> {
        });
    }

    private BStatsRegistration(JavaPlugin plugin, int pluginId, boolean active, Runnable closeAction) {
        this.plugin = plugin;
        this.pluginId = pluginId;
        this.active = active;
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public int pluginId() {
        return pluginId;
    }

    public boolean active() {
        return active && !closed.get();
    }

    public boolean closed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }
}
