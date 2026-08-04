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
     * <p>A no-op handle is returned instead of an exception when the owner is {@code null} or disabled,
     * the spec is {@code null} or has a blank/unusable id, the key is already held by a registration this
     * owner does not own, the server platform refuses the advancement, the advancement feature is switched
     * off in config, EmakiCodex is not enabled, or the caller is not on the global thread. Because
     * {@link AdvancementRegistration} exposes only {@code close()}, the handle itself cannot be inspected
     * to tell a successful registration from a rejected one; verify through
     * {@code EmakiCodexApi.catalog()} when that distinction matters.
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
     * <p>A no-op handle is returned when the owner is {@code null} or disabled, the trigger is
     * {@code null}, its id is blank, its id accessor throws, or EmakiCodex's trigger registry is not
     * built. Registering the same owner and trigger id again replaces the previous entry. As with
     * advancement registration, the returned handle cannot be inspected to confirm success.
     *
     * @param owner   plugin that owns the registration lifecycle
     * @param trigger the trigger provider to register
     * @return a closeable handle, or a no-op handle when nothing was registered
     */
    @NotNull AdvancementTriggerRegistration registerTrigger(
            @Nullable Plugin owner, @Nullable AdvancementTrigger trigger);
}
