package emaki.jiuwu.craft.gem.service;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.integration.SkillPdcGateway;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemPdcAttributeWriter {

    private static final String SOURCE_ID = "gem";

    private final EmakiGemPlugin plugin;
    private final PdcAttributeGateway gateway;
    private final SkillPdcGateway skillPdcGateway = new SkillPdcGateway();

    public GemPdcAttributeWriter(EmakiGemPlugin plugin, PdcAttributeGateway gateway) {
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

    public void applySkills(ItemStack itemStack, Iterable<String> skillIds) {
        if (itemStack == null) {
            return;
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (skillIds != null) {
            for (String skillId : skillIds) {
                ids.add(skillId);
            }
        }
        skillPdcGateway.write(itemStack, ids);
    }

    public void copyOtherSources(ItemStack original, ItemStack rebuilt) {
        if (gateway != null && original != null && rebuilt != null) {
            gateway.copyPayloads(original, rebuilt, Set.of(SOURCE_ID));
        }
        skillPdcGateway.copy(original, rebuilt);
    }

    public boolean available() {
        return gateway != null && gateway.available();
    }
}
