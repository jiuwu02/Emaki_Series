package emaki.jiuwu.craft.level.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Third-party gameplay experience source extension point. */
@ApiStatus.NonExtendable
public interface LevelExtensions {

    /**
     * Registers an owner-scoped experience source provider.
     *
     * <p>Registration is allowed from any thread. Providers are keyed by owner and normalized provider
     * id, so registering the same id again for the same owner replaces the previous provider. Closing
     * the returned handle is idempotent, and a superseded handle never removes its replacement. The
     * runtime also drops all of an owner's providers when that plugin is disabled, so a handle does
     * not need to be closed during a normal shutdown.
     *
     * <p>Registered providers are later invoked on the context player's owner thread, once per
     * gameplay trigger; see {@link ExpSourceProvider#provide}.
     *
     * @param owner    the plugin the registration belongs to; must be non-{@code null} and enabled,
     *                 otherwise an inactive handle is returned
     * @param provider the provider to register; must be non-{@code null} and expose a non-blank
     *                 {@code id()}, otherwise an inactive handle is returned
     * @return a closeable registration handle. Never {@code null}; rejected input and a missing runtime
     *         both yield {@link ExpSourceRegistration#noop()}, which is inert rather than an error, so
     *         verify availability through {@code EmakiLevelApi.status()} instead of inspecting the
     *         handle
     */
    @NotNull
    ExpSourceRegistration registerExpSource(@Nullable Plugin owner, @Nullable ExpSourceProvider provider);
}
