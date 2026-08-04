package emaki.jiuwu.craft.skills.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-scoped extension points for EmakiSkills.
 *
 * <p>The script-action registry is gone: skill scripts are now CoreLib pipelines, so a third party adds a stage
 * by registering it with {@code EmakiCoreLib}'s stage registry rather than with EmakiSkills. One registry means
 * one place where a stage id can collide, and the stage becomes available to every module at once.</p>
 */
@ApiStatus.NonExtendable
public interface SkillExtensions {

    /**
     * Registers an external source that can unlock skills for players.
     *
     * <p>Registrations are keyed by owner plus the provider's normalized {@link SkillSourceProvider#id()}
     * (trimmed and lower-cased with {@code Locale.ROOT}), so two different plugins may safely use the same
     * provider id. Registering again under the same owner and id supersedes the previous entry.
     *
     * <p><strong>Handle lifecycle:</strong> close the returned handle when your plugin tears down its
     * integration. Closing is idempotent: repeated
     * closes do nothing, and a superseded handle is inert — it will not remove the replacement that took its
     * place. EmakiSkills also drops every registration owned by a plugin when that plugin is disabled, so a
     * missed close does not leak past disable; the handle still matters for unregistering earlier than that.
     *
     * <p><strong>Thread:</strong> registration is internally synchronized and may be called from any thread.
     * Callbacks on the provider itself are invoked by the runtime while collecting a player's unlocked
     * skills, so the provider must be safe to invoke on the player's owner thread.
     *
     * @param owner    the registering plugin, used for automatic cleanup on disable; {@code null} or an
     *                 already-disabled plugin yields an inactive no-op handle instead of an exception
     * @param provider the source implementation; {@code null}, a blank {@code id()}, or an {@code id()} that
     *                 throws all yield an inactive no-op handle
     * @return a closeable handle for this registration, never {@code null}; the handle is inert when the
     *         arguments were rejected or EmakiSkills is unavailable
     */
    @NotNull SkillSourceRegistration registerSkillSource(
            @Nullable Plugin owner, @Nullable SkillSourceProvider provider);
}
