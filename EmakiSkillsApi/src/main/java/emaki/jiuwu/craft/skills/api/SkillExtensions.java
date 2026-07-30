package emaki.jiuwu.craft.skills.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Owner-scoped extension points for EmakiSkills. */
@ApiStatus.NonExtendable
public interface SkillExtensions {

    /** Script-action registry. Never {@code null}; empty when EmakiSkills is unavailable. */
    @NotNull SkillScriptActionRegistry scriptActions();

    /** Registers one script action. */
    @NotNull SkillActionResult registerScriptAction(@Nullable Plugin owner, @Nullable SkillScriptAction action);

    /** Removes every script action owned by a plugin. */
    void unregisterScriptActions(@Nullable Plugin owner);

    /**
     * Registers an external skill source. The owner is also cleaned up automatically on plugin disable.
     * Re-registering the same owner/provider id supersedes the old registration handle.
     */
    @NotNull SkillSourceRegistration registerSkillSource(
            @Nullable Plugin owner, @Nullable SkillSourceProvider provider);
}
