package emaki.jiuwu.craft.mobs.apiimpl;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.api.EmakiMobsApi;
import emaki.jiuwu.craft.mobs.api.MobCatalog;
import emaki.jiuwu.craft.mobs.api.MobExtensions;
import emaki.jiuwu.craft.mobs.api.MobOperations;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class ServiceBackedMobsBridge implements EmakiMobsApi.Bridge {

    private static final String API_VERSION = "1.0.0";

    private final EmakiMobsPlugin plugin;
    private final MobCatalog catalog;

    public ServiceBackedMobsBridge(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultMobCatalog(plugin);
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
    public @NotNull MobCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull MobOperations operations() {
        return new MobOperations() {
            @Override
            public Optional<LivingEntity> spawn(Location location, String mobId) {
                if (!plugin.isEnabled() || plugin.isShutdownStarted()) return Optional.empty();
                var factory = plugin.mobFactory();
                if (factory == null) return Optional.empty();
                return factory.spawn(location, mobId);
            }

            @Override
            public void remove(LivingEntity entity) {
                if (!plugin.isEnabled() || plugin.isShutdownStarted()) return;
                entity.remove();
            }
        };
    }

    @Override
    public @NotNull MobExtensions extensions() {
        MobExtensions e = plugin.mobExtensions();
        return e != null ? e : (id, s) -> {};
    }
}
