package emaki.jiuwu.craft.mobs.api;

import emaki.jiuwu.craft.mobs.api.model.MobDefinition;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only query layer for EmakiMobs mob definitions.
 *
 * <p>Definition and id-set queries return snapshots and are safe to call from any thread.
 * {@link #identify(LivingEntity)} reads live entity state and requires that entity's owner thread.
 * An empty answer does not necessarily mean EmakiMobs is absent;
 * check {@link EmakiMobsApi#status()} to distinguish "not installed"
 * from "installed but no definitions loaded".
 */
public interface MobCatalog {

    /**
     * {@return the definition for the given mob id, or empty when not registered}
     */
    Optional<MobDefinition> definition(@NotNull String mobId);

    /**
     * {@return an unmodifiable snapshot of all currently registered mob ids}
     */
    @NotNull
    Set<String> registeredIds();

    /**
     * Finds the managed mob id stored on a live entity.
     *
     * <p>This method reads entity PDC and must be called on the entity owner thread. It returns empty
     * when the entity is null, not managed, EmakiMobs is not ready, or its definition is no longer loaded.
     * The definition and id-set queries above remain safe to call from any thread.
     *
     * @param entity entity to inspect, or {@code null}
     * @return the currently loaded managed mob id, or empty
     */
    @NotNull
    Optional<String> identify(@Nullable LivingEntity entity);
}
