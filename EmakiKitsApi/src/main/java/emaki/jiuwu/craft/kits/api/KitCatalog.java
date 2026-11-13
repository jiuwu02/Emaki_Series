package emaki.jiuwu.craft.kits.api;

import emaki.jiuwu.craft.kits.api.model.KitView;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only query layer over the kit definitions loaded by EmakiKits.
 *
 * <p>All methods return snapshots or primitive values and are safe to call from any thread. An
 * empty result does not necessarily mean EmakiKits is absent; check
 * {@link EmakiKitsApi#status()} to distinguish "not installed" from "installed but no kit loaded".
 */
@ApiStatus.NonExtendable
public interface KitCatalog {

    /**
     * {@return an unmodifiable snapshot of every currently loaded kit id}
     */
    @NotNull
    Set<String> kitIds();

    /**
     * {@return the view for the given id, or empty when the kit is not loaded}
     *
     * @param kitId kit id to resolve; normalized with {@code Locale.ROOT} by the runtime
     */
    @NotNull
    Optional<KitView> kit(@Nullable String kitId);

    /**
     * {@return whether the given kit currently passes its configured time window}
     *
     * <p>A kit without an enabled {@code limit} rule always reports {@code true}. An unknown id
     * reports {@code false}.
     *
     * @param kitId kit id to test
     */
    boolean withinTimeWindow(@Nullable String kitId);

    /**
     * {@return the player's accumulated play time in seconds, or {@code 0} while unavailable}
     *
     * <p>The value comes from the platform {@code PLAY_ONE_MINUTE} statistic and is used by the
     * join invite-code missed window. It is a read-only observation and never mutates state.
     *
     * @param player the player to inspect; {@code null} yields {@code 0}
     */
    long playedSeconds(@Nullable Player player);
}
