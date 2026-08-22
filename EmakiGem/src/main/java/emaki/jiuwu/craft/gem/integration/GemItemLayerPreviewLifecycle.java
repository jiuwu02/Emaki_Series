package emaki.jiuwu.craft.gem.integration;

import emaki.jiuwu.craft.corelib.integration.AbstractPluginIntegrationLifecycle;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemItemLayerPreviewLifecycle extends AbstractPluginIntegrationLifecycle<EmakiGemPlugin> {

    private static final String ITEM_PLUGIN_NAME = "EmakiItem";
    private static final String PROVIDER_CLASS =
            "emaki.jiuwu.craft.gem.integration.item.GemItemLayerPreviewProvider";

    public GemItemLayerPreviewLifecycle(EmakiGemPlugin plugin) {
        super(plugin, EmakiGemPlugin.class, ITEM_PLUGIN_NAME, PROVIDER_CLASS, "EmakiItem gem preview integration");
    }
}
