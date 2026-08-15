package emaki.jiuwu.craft.skills.apiimpl;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillExtensions;
import emaki.jiuwu.craft.skills.api.SkillSourceProvider;
import emaki.jiuwu.craft.skills.api.SkillSourceRegistration;

public final class DefaultSkillExtensions implements SkillExtensions {

    private final EmakiSkillsPlugin plugin;

    public DefaultSkillExtensions(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull SkillSourceRegistration registerSkillSource(@Nullable Plugin owner,
            @Nullable SkillSourceProvider provider) {
        return !plugin.isEnabled() || plugin.skillSourceRegistry() == null
                ? SkillSourceRegistration.noop()
                : plugin.skillSourceRegistry().register(owner, provider);
    }
}
