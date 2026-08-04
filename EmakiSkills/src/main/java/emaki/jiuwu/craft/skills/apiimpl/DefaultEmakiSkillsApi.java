package emaki.jiuwu.craft.skills.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
import emaki.jiuwu.craft.skills.api.SkillCatalog;
import emaki.jiuwu.craft.skills.api.SkillExtensions;
import emaki.jiuwu.craft.skills.api.SkillOperations;

/**
 * {@link EmakiSkillsApi.Bridge} 的运行时实现。
 */
public final class DefaultEmakiSkillsApi implements EmakiSkillsApi.Bridge {

    private final EmakiSkillsPlugin plugin;
    private final DefaultSkillCatalog catalog;
    private final SkillOperations operations;
    private final SkillExtensions extensions;

    public DefaultEmakiSkillsApi(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultSkillCatalog(plugin);
        this.operations = new DefaultSkillOperations(plugin, catalog);
        this.extensions = new DefaultSkillExtensions(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        boolean ready = plugin.skillRegistryService() != null
                && plugin.playerSkillDataStore() != null
                && plugin.playerSkillStateService() != null
                && plugin.castAttemptService() != null
                && plugin.skillUpgradeService() != null
                && plugin.skillSourceRegistry() != null
                && plugin.skillPipelineRuntime() != null;
        return ready
                ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override
    public @NotNull SkillCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull SkillOperations operations() {
        return operations;
    }

    @Override
    public @NotNull SkillExtensions extensions() {
        return extensions;
    }
}
