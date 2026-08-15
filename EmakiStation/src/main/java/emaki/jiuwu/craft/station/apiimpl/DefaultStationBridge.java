package emaki.jiuwu.craft.station.apiimpl;

import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.api.EmakiStationApi;
import emaki.jiuwu.craft.station.api.StationCatalog;
import emaki.jiuwu.craft.station.api.StationExtensions;
import emaki.jiuwu.craft.station.api.StationOperations;

public final class DefaultStationBridge implements EmakiStationApi.Bridge {

    private static final String API_VERSION = "1.0.0";

    private static final AtomicReference<DefaultStationBridge> ACTIVE = new AtomicReference<>();

    private final EmakiStationPlugin plugin;
    private final StationCatalog catalog;
    private final StationOperations operations;
    private final StationExtensions extensions;

    public DefaultStationBridge(EmakiStationPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultStationCatalog(plugin);
        this.operations = new DefaultStationOperations(plugin);
        this.extensions = new DefaultStationExtensions();
        ACTIVE.set(this);
    }

    public static void uninstallActive() {
        DefaultStationBridge active = ACTIVE.getAndSet(null);
        if (active != null) {
            EmakiStationApi.uninstall(active);
        }
    }

    @Override
    public @NotNull ApiStatus status() {
        if (plugin == null || !plugin.isEnabled() || plugin.isShutdownStarted()) {
            return ApiStatus.notInstalled();
        }

        return plugin.contentReady()
                ? ApiStatus.ready(plugin.getName(), plugin.getPluginMeta().getVersion(), API_VERSION)
                : ApiStatus.loading(plugin.getName(), plugin.getPluginMeta().getVersion(), API_VERSION);
    }

    @Override
    public @NotNull StationCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull StationOperations operations() {
        return operations;
    }

    @Override
    public @NotNull StationExtensions extensions() {
        return extensions;
    }

    private static final class DefaultStationExtensions implements StationExtensions {
    }
}
