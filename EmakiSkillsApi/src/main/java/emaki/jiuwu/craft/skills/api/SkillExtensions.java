package emaki.jiuwu.craft.skills.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-scoped extension points for EmakiSkills.
 *
 * <p>Two things can be contributed. A skill <em>source</em> decides which skill ids a player has unlocked, and a
 * skill <em>definition</em> decides what a skill id actually does. Register a definition for a skill that does
 * not exist in {@code skills/*.yml}, and a source for a skill that does — the two compose freely.</p>
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

    /**
     * Registers an external skill definition.
     *
     * <p>Registrations are keyed by owner plus the definition's normalized {@link ExternalSkillDefinition#id()},
     * so two plugins may define different skills under the same id without colliding. A registered definition
     * takes precedence over a YAML skill with the same id.
     *
     * <p>Registering or removing a definition discards the compiled skill pipelines, so a replacement takes
     * effect on the next cast without a {@code /eskills reload}. The definition survives a config reload for as
     * long as the owning plugin stays enabled.
     *
     * <p><strong>Handle lifecycle:</strong> close the returned handle when your plugin tears down its
     * integration. Closing is idempotent, a superseded handle is inert, and EmakiSkills drops every
     * registration owned by a plugin when that plugin is disabled.
     *
     * <p><strong>Thread:</strong> registration is internally synchronized and may be called from any thread.
     * The definition's accessors are read later by the runtime, including on player owner threads, so they must
     * be side-effect-free.
     *
     * @param owner      the registering plugin, used for automatic cleanup on disable; {@code null} or an
     *                   already-disabled plugin yields an inactive no-op handle instead of an exception
     * @param definition the definition to register; {@code null}, a blank {@code id()}, an {@code id()} that
     *                   throws, or a definition that cannot be mapped all yield an inactive no-op handle
     * @return a closeable handle for this registration, never {@code null}; the handle is inert when the
     *         arguments were rejected or EmakiSkills is unavailable
     */
    @NotNull SkillDefinitionRegistration registerSkillDefinition(
            @Nullable Plugin owner, @Nullable ExternalSkillDefinition definition);

    /**
     * Removes every external skill definition a plugin registered.
     *
     * @param owner the plugin whose registrations are removed
     */
    void unregisterSkillDefinitions(@Nullable Plugin owner);
}
