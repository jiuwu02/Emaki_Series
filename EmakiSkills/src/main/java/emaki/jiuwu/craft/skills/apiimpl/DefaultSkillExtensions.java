package emaki.jiuwu.craft.skills.apiimpl;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillExtensions;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;
import emaki.jiuwu.craft.skills.api.SkillSourceProvider;
import emaki.jiuwu.craft.skills.api.SkillSourceRegistration;

/** Runtime extension layer backed by owner-scoped registries. */
public final class DefaultSkillExtensions implements SkillExtensions {

    private final EmakiSkillsPlugin plugin;

    public DefaultSkillExtensions(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull SkillScriptActionRegistry scriptActions() {
        SkillScriptActionRegistry registry = plugin.isEnabled() ? plugin.skillScriptActionRegistry() : null;
        return registry != null ? registry : SkillScriptActionRegistry.empty();
    }

    @Override
    public @NotNull SkillActionResult registerScriptAction(@Nullable Plugin owner,
            @Nullable SkillScriptAction action) {
        SkillScriptActionRegistry registry = plugin.isEnabled() ? plugin.skillScriptActionRegistry() : null;
        if (registry == null) {
            return SkillActionResult.failure(SkillActionErrorType.PROVIDER_UNAVAILABLE,
                    "EmakiSkills script action registry is unavailable.");
        }
        if (owner == null || action == null) {
            return SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                    "Owner and action must not be null.");
        }
        return registry.register(owner, action);
    }

    @Override
    public void unregisterScriptActions(@Nullable Plugin owner) {
        SkillScriptActionRegistry registry = plugin.skillScriptActionRegistry();
        if (registry != null && owner != null) {
            registry.unregisterAll(owner);
        }
    }

    @Override
    public @NotNull SkillSourceRegistration registerSkillSource(@Nullable Plugin owner,
            @Nullable SkillSourceProvider provider) {
        return !plugin.isEnabled() || plugin.skillSourceRegistry() == null
                ? SkillSourceRegistration.noop()
                : plugin.skillSourceRegistry().register(owner, provider);
    }
}
