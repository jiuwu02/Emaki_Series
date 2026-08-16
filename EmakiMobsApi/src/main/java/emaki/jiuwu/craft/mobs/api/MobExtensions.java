package emaki.jiuwu.craft.mobs.api;

import org.jetbrains.annotations.NotNull;

/**
 * Extension registration API for EmakiMobs.
 *
 * <p>Third-party plugins can register custom spawners that receive a callback
 * each time EmakiMobs reloads, allowing them to adapt to configuration changes.
 *
 * <p>Obtain the current instance via {@link EmakiMobsApi#extensions()} rather
 * than caching it in a field — the backing implementation is replaced across reloads.
 */
public interface MobExtensions {

    /**
     * Registers a custom spawner identified by the given {@code id}.
     *
     * <p>{@link CustomSpawner#onReload()} is called immediately upon registration,
     * and again after every EmakiMobs config reload.
     *
     * @param id      a unique identifier for this spawner (used for deduplication)
     * @param spawner the spawner implementation to register
     */
    void registerCustomSpawner(@NotNull String id, @NotNull CustomSpawner spawner);

    /**
     * Callback contract for custom mob spawners registered via
     * {@link MobExtensions#registerCustomSpawner}.
     */
    @FunctionalInterface
    interface CustomSpawner {

        /**
         * Called once on registration and again after every EmakiMobs reload.
         * Use this to read updated config values and (re)schedule spawn tasks.
         */
        void onReload();
    }
}
