package emaki.jiuwu.craft.mobs.api;

import emaki.jiuwu.craft.mobs.api.model.MobDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only query layer for EmakiMobs mob definitions.
 *
 * <p>All methods return snapshots and are safe to call from any thread.
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
}
