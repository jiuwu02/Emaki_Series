package emaki.jiuwu.craft.corelib.api.dialog;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/**
 * Vanilla dialog access, reached through {@code EmakiCoreLibApi.dialogs()}.
 *
 * <p>Dialog definitions live in EmakiCoreLib's dialog directory and are authored by the server
 * owner. Third-party plugins only show them by id.
 *
 * <p>Showing a dialog requires a client on Minecraft 1.21.6 or newer. Behaviour on older clients is
 * unverified.
 *
 * <p>This layer replaces the former standalone {@code DialogApi} static facade.
 */
@ApiStatus.NonExtendable
public interface CoreLibDialogs {

    /** {@return whether the dialog subsystem is enabled and usable} */
    boolean enabled();

    /** {@return the ids of every loaded dialog; empty when unavailable} */
    @NotNull
    Collection<String> dialogIds();

    /**
     * Tests whether one dialog id is currently loaded, so callers can distinguish "the id is wrong"
     * from "showing it failed".
     *
     * <p>The id is normalised before lookup: trimmed, lower-cased with {@link java.util.Locale#ROOT},
     * and spaces replaced with underscores. A {@code null} or blank id yields {@code false}, as does a
     * disabled or unavailable dialog subsystem; check {@link #enabled()} to tell those apart. Callable
     * from any thread.
     *
     * @param dialogId the dialog id
     * @return whether a dialog with that id is loaded
     */
    boolean contains(@Nullable String dialogId);

    /**
     * Shows a dialog to a player.
     *
     * <p><strong>Thread:</strong> must be called on the thread that owns {@code player}. On Folia
     * that is the player's region thread; use
     * {@link emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling#runForEntity} to hop.
     *
     * @param player   the target player
     * @param dialogId the dialog id
     * @return success, or a failure describing why the dialog was not shown
     */
    @NotNull
    EmakiResult<Unit> show(@Nullable Player player, @Nullable String dialogId);

    /**
     * Closes the player's current dialog.
     *
     * <p><strong>Thread:</strong> must be called on the thread that owns {@code player}.
     *
     * @param player the target player
     * @return success when a close request was issued, otherwise a failure
     */
    @NotNull
    EmakiResult<Unit> close(@Nullable Player player);
}
