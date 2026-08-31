package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;

public final class OraxenBlockBridgeProvider extends LazyPluginBridge<CustomBlockBridge> {

    private static final String PLUGIN_NAME = "Oraxen";
    private static final CustomBlockBridge NOOP = new NoopCustomBlockBridge();

    public OraxenBlockBridgeProvider(JavaPlugin owner) {
        super(owner, PLUGIN_NAME, NOOP);
    }

    @Override
    protected CustomBlockBridge createDelegate() {
        return new OraxenBlockBridgeApi();
    }
}
