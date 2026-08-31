package emaki.jiuwu.craft.strengthen.integration;

import emaki.jiuwu.craft.corelib.integration.AbstractPluginIntegrationLifecycle;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenItemLayerPreviewLifecycle
        extends AbstractPluginIntegrationLifecycle<EmakiStrengthenPlugin> {

    private static final String ITEM_PLUGIN_NAME = "EmakiItem";
    private static final String PROVIDER_CLASS =
            "emaki.jiuwu.craft.strengthen.integration.item.StrengthenItemLayerPreviewProvider";

    public StrengthenItemLayerPreviewLifecycle(EmakiStrengthenPlugin plugin) {
        super(plugin, EmakiStrengthenPlugin.class, ITEM_PLUGIN_NAME, PROVIDER_CLASS,
                "EmakiItem strengthen preview integration");
    }
}
