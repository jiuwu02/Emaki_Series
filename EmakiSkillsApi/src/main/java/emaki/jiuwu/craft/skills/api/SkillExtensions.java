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
     * Registers an external skill source. The owner is also cleaned up automatically on plugin disable.
     * Re-registering the same owner/provider id supersedes the old registration handle.
     */
    @NotNull SkillSourceRegistration registerSkillSource(
            @Nullable Plugin owner, @Nullable SkillSourceProvider provider);
}
