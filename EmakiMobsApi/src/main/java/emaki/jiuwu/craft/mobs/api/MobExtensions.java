package emaki.jiuwu.craft.mobs.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * Registers an owner-scoped custom spawner identified by the given {@code id}.
     *
     * <p>{@link CustomSpawner#onReload()} is called immediately upon registration, and again after
     * every EmakiMobs config reload. The returned handle is the precise registration and should be
     * closed when the caller no longer needs it.
     *
     * @param owner   plugin that owns the registration; may be {@code null}, but then automatic owner
     *                disable cleanup is unavailable
     * @param id      unique identifier for this spawner; ids are normalized for deduplication
     * @param spawner spawner implementation to register
     * @return a closeable registration handle, or a no-op handle for invalid input
     */
    @NotNull
    MobSpawnerRegistration registerCustomSpawner(@Nullable Plugin owner,
                                                 @Nullable String id,
                                                 @Nullable CustomSpawner spawner);

    /**
     * Removes every custom spawner registered by the given owner.
     *
     * @param owner plugin whose registrations should be removed
     */
    void unregisterCustomSpawners(@Nullable Plugin owner);

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
