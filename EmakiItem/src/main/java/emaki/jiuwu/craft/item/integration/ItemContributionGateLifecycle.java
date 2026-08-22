package emaki.jiuwu.craft.item.integration;

import emaki.jiuwu.craft.corelib.integration.AbstractPluginIntegrationLifecycle;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemContributionGateLifecycle extends AbstractPluginIntegrationLifecycle<EmakiItemPlugin> {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String GATE_CLASS =
            "emaki.jiuwu.craft.item.integration.attribute.ItemConditionContributionGate";

    public ItemContributionGateLifecycle(EmakiItemPlugin plugin) {
        super(plugin, EmakiItemPlugin.class, ATTRIBUTE_PLUGIN_NAME, GATE_CLASS,
                "EmakiAttribute item condition gate");
    }
}
