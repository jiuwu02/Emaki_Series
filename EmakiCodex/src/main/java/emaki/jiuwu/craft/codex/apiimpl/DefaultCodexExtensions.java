package emaki.jiuwu.craft.codex.apiimpl;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.api.AdvancementRegistration;
import emaki.jiuwu.craft.codex.api.AdvancementSpec;
import emaki.jiuwu.craft.codex.api.AdvancementTrigger;
import emaki.jiuwu.craft.codex.api.AdvancementTriggerRegistration;
import emaki.jiuwu.craft.codex.api.CodexExtensions;

public final class DefaultCodexExtensions implements CodexExtensions {

    private final EmakiCodexPlugin plugin;

    public DefaultCodexExtensions(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull AdvancementRegistration registerAdvancement(
            @Nullable Plugin owner, @Nullable AdvancementSpec spec) {
        if (!plugin.isEnabled() || plugin.appConfig() == null || !plugin.appConfig().advancementEnabled()
                || plugin.advancementRegistrar() == null
                || plugin.threadOwnership() == null || !plugin.threadOwnership().isGlobalOwned()) {
            return AdvancementRegistration.noop();
        }
        return plugin.advancementRegistrar().register(owner, spec);
    }

    @Override
    public @NotNull AdvancementTriggerRegistration registerTrigger(
            @Nullable Plugin owner, @Nullable AdvancementTrigger trigger) {
        return !plugin.isEnabled() || plugin.advancementTriggerRegistry() == null
                ? AdvancementTriggerRegistration.noop()
                : plugin.advancementTriggerRegistry().register(owner, trigger);
    }
}
