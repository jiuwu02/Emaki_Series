package emaki.jiuwu.craft.corelib.integration.furniture;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;

public final class OraxenFurnitureBridgeProvider extends LazyFurnitureBridge {

    private static final String PLUGIN_NAME = "Oraxen";

    public OraxenFurnitureBridgeProvider(JavaPlugin owner) {
        super(owner, PLUGIN_NAME);
    }

    @Override
    protected FurnitureBridge createDelegate() {
        return new OraxenFurnitureBridgeApi();
    }
}
