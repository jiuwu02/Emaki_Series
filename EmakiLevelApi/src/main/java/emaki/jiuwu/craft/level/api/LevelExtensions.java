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
     * id. Closing the returned handle is idempotent, and a superseded handle never removes its
     * replacement.
     */
    @NotNull
    ExpSourceRegistration registerExpSource(@Nullable Plugin owner, @Nullable ExpSourceProvider provider);
}
