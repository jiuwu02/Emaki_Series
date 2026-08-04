package emaki.jiuwu.craft.gem.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.EmakiGemApi;
import emaki.jiuwu.craft.gem.api.GemCatalog;
import emaki.jiuwu.craft.gem.api.GemOperations;

/**
 * {@link EmakiGemApi.Bridge} 的运行时实现。
 *
 * <p>两个层实现只构造一次并复用。{@link #status()} 把「插件已启用」与「状态服务已就绪」分别映射到
 * {@code installed} 与 {@code ready}，覆盖 install 早于子系统加载完成的空档窗口。
 */
public final class DefaultEmakiGemApi implements EmakiGemApi.Bridge {

    private final EmakiGemPlugin plugin;
    private final GemCatalog catalog;
    private final GemOperations operations;

    public DefaultEmakiGemApi(EmakiGemPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultGemCatalog(plugin);
        this.operations = new DefaultGemOperations(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        boolean ready = plugin.publicApiReady()
                && plugin.gemLoader() != null
                && plugin.gemItemLoader() != null
                && plugin.stateService() != null
                && plugin.itemMatcher() != null
                && plugin.itemFactory() != null
                && plugin.snapshotBuilder() != null
                && plugin.inlayService() != null
                && plugin.socketOpenerService() != null
                && plugin.gemGuiService() != null
                && plugin.resonanceService() != null;
        return ready
                ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override
    public @NotNull GemCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull GemOperations operations() {
        return operations;
    }
}
