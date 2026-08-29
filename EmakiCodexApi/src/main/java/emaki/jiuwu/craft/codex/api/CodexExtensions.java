package emaki.jiuwu.craft.codex.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Owner-scoped extension points for external advancements and trigger providers. */
@ApiStatus.NonExtendable
public interface CodexExtensions {

    /**
     * Registers one advancement in the owner's namespace. Must run on the server global thread.
     * The owner is removed automatically when disabled.
     *
     * <p>Keep the returned handle and close it in {@code onDisable}; closing is idempotent. Re-registering
     * the same owner and id replaces the owner's previous registration rather than adding a second one,
     * and closing the superseded handle does not remove the replacement.
     *
     * <p>Invalid, unavailable, conflicting or wrong-thread registrations return an inactive handle rather
     * than throwing. The handle exposes no success flag; query {@code EmakiCodexApi.catalog()} when the
     * distinction matters.
     *
     * @param owner plugin that owns the registration lifecycle and supplies the namespace
     * @param spec  the advancement definition to register
     * @return a closeable handle, or a no-op handle when nothing was registered
     */
    @NotNull AdvancementRegistration registerAdvancement(
            @Nullable Plugin owner, @Nullable AdvancementSpec spec);

    /**
     * Registers a trigger provider, automatically removed when its owner is disabled.
     *
     * <p>Unlike {@link #registerAdvancement} this does not require the global thread. Providers are
     * consulted in priority order when EmakiCodex dispatches a trigger, and a provider whose owner has
     * been disabled is dropped at dispatch time.
     *
     * <p>Invalid or unavailable registrations return an inactive handle rather than throwing. Registering
     * the same owner and trigger id replaces the previous entry; closing the superseded handle cannot remove
     * the replacement. The returned handle exposes no success flag.
     *
     * @param owner   plugin that owns the registration lifecycle
     * @param trigger the trigger provider to register
     * @return a closeable handle, or a no-op handle when nothing was registered
     */
    @NotNull AdvancementTriggerRegistration registerTrigger(
            @Nullable Plugin owner, @Nullable AdvancementTrigger trigger);
}
