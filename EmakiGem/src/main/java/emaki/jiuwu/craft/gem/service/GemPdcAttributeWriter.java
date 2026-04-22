package emaki.jiuwu.craft.gem.service;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemPdcAttributeWriter {

    private static final String SOURCE_ID = "gem";

    private final EmakiGemPlugin plugin;
    private final ReflectivePdcAttributeGateway gateway;

    public GemPdcAttributeWriter(EmakiGemPlugin plugin, ReflectivePdcAttributeGateway gateway) {
        this.plugin = plugin;
        this.gateway = gateway;
    }

    public void apply(ItemStack itemStack, Map<String, Double> attributes, Map<String, String> meta) {
        if (gateway == null || itemStack == null) {
            return;
        }
        if (attributes == null || attributes.isEmpty()) {
            gateway.clear(itemStack, SOURCE_ID);
            return;
        }
        gateway.write(itemStack, SOURCE_ID, attributes, meta);
    }

    public void clear(ItemStack itemStack) {
        if (gateway != null && itemStack != null) {
            gateway.clear(itemStack, SOURCE_ID);
        }
    }

    public void copyOtherSources(ItemStack original, ItemStack rebuilt) {
        if (gateway != null && original != null && rebuilt != null) {
            gateway.copyPayloads(original, rebuilt, Set.of(SOURCE_ID));
        }
    }

    public boolean available() {
        return gateway != null && gateway.available();
    }
}
