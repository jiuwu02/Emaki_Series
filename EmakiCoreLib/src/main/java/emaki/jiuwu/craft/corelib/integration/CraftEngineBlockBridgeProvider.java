package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;

public final class CraftEngineBlockBridgeProvider extends LazyPluginBridge<CraftEngineBlockBridge>
        implements CraftEngineBlockBridge {

    private static final String PLUGIN_NAME = "CraftEngine";
    private static final CraftEngineBlockBridge NOOP = new NoopCraftEngineBlockBridgeAdapter();

    public CraftEngineBlockBridgeProvider(JavaPlugin owner) {
        super(owner, PLUGIN_NAME, NOOP);
    }

    @Override
    protected CraftEngineBlockBridge createDelegate() {
        return new CraftEngineBlockBridgeApi();
    }

    private static final class NoopCraftEngineBlockBridgeAdapter extends NoopCustomBlockBridge implements CraftEngineBlockBridge {
    }
}
