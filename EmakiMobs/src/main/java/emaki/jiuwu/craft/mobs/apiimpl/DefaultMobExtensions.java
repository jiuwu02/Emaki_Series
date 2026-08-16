package emaki.jiuwu.craft.mobs.apiimpl;

import emaki.jiuwu.craft.mobs.api.MobExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link MobExtensions}.
 *
 * <p>Holds registered {@link MobExtensions.CustomSpawner} instances and
 * notifies them whenever EmakiMobs reloads.
 */
public final class DefaultMobExtensions implements MobExtensions {

    private final Map<String, CustomSpawner> spawners = new ConcurrentHashMap<>();

    @Override
    public void registerCustomSpawner(@NotNull String id, @NotNull CustomSpawner spawner) {
        spawners.put(id, spawner);
        spawner.onReload();
    }

    /**
     * Notifies all registered spawners that EmakiMobs has reloaded.
     * Called by {@link emaki.jiuwu.craft.mobs.MobsLifecycleCoordinator} after each reload.
     */
    public void notifyReload() {
        spawners.values().forEach(CustomSpawner::onReload);
    }
}
