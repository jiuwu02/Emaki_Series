package emaki.jiuwu.craft.mobs.api;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Provides runtime mob-spawning and management operations.
 *
 * <p>Obtain the current instance via {@link EmakiMobsApi#operations()} rather than
 * caching it in a field — the backing implementation is replaced across reloads.
 *
 * <p>While EmakiMobs is absent or not yet ready, the returned instance is a safe
 * no-op implementation that always returns {@link Optional#empty()}.
 */
public interface MobOperations {

    /**
     * Spawns a registered mob at the given location.
     *
     * @param location target spawn location; must have a non-null world
     * @param mobId    registered mob definition id (case-sensitive)
     * @return the spawned entity wrapped in an Optional,
     *         or empty when the mob id is unknown or spawn failed
     */
    @NotNull
    Optional<LivingEntity> spawn(@NotNull Location location, @NotNull String mobId);
}
