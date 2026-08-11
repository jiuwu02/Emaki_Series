package emaki.jiuwu.craft.codex.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.api.CodexCatalog;
import emaki.jiuwu.craft.codex.api.CodexExtensions;
import emaki.jiuwu.craft.codex.api.CodexOperations;
import emaki.jiuwu.craft.codex.api.EmakiCodexApi;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;

/** Runtime bridge for the static Codex facade. */
public final class DefaultEmakiCodexApi implements EmakiCodexApi.Bridge {

    private final EmakiCodexPlugin plugin;
    private final CodexCatalog catalog;
    private final CodexOperations operations;
    private final CodexExtensions extensions;

    public DefaultEmakiCodexApi(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultCodexCatalog(plugin);
        this.operations = new DefaultCodexOperations(plugin);
        this.extensions = new DefaultCodexExtensions(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        boolean ready = plugin.contentReady()
                && plugin.advancementRegistrar() != null
                && plugin.advancementService() != null
                && plugin.advancementTriggerRegistry() != null
                && plugin.threadOwnership() != null;
        return ready ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override public @NotNull CodexCatalog catalog() { return catalog; }
    @Override public @NotNull CodexOperations operations() { return operations; }
    @Override public @NotNull CodexExtensions extensions() { return extensions; }
}
