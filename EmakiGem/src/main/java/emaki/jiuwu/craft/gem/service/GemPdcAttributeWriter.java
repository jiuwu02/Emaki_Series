package emaki.jiuwu.craft.gem.service;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.integration.GemAttributeBridge;
import emaki.jiuwu.craft.skills.protocol.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.protocol.SkillPdcMutation;

public final class GemPdcAttributeWriter {

    private static final String SOURCE_ID = "gem";

    private final EmakiGemPlugin plugin;
    private final GemAttributeBridge gateway;

    public GemPdcAttributeWriter(EmakiGemPlugin plugin, GemAttributeBridge gateway) {
        this.plugin = plugin;
        this.gateway = gateway;
    }

    public void apply(ItemStack itemStack, Map<String, Double> attributes, Map<String, String> meta) {
        apply(itemStack, attributes);
    }

    public void apply(ItemStack itemStack, Map<String, Double> attributes) {
        if (gateway == null || itemStack == null) {
            return;
        }
        if (attributes == null || attributes.isEmpty()) {
            gateway.clear(itemStack, SOURCE_ID);
            return;
        }
        gateway.write(itemStack, SOURCE_ID, attributes, Map.of());
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
        observeSkillMutation(itemStack, EquipmentSkillPdcCodec.write(itemStack, skillIds));
    }

    public void copyOtherSources(ItemStack original, ItemStack rebuilt) {
        if (gateway != null && original != null && rebuilt != null) {
            gateway.copyPayloads(original, rebuilt, Set.of(SOURCE_ID));
        }
        observeSkillMutation(rebuilt, EquipmentSkillPdcCodec.copy(original, rebuilt));
    }

    public boolean available() {
        return gateway != null && gateway.available();
    }

    private void observeSkillMutation(ItemStack itemStack, SkillPdcMutation mutation) {
        if (plugin == null
                || plugin.debugLogger() == null
                || mutation == null
                || !plugin.debugLogger().shouldLog("pdc", (java.util.UUID) null)) {
            return;
        }
        plugin.debugLogger().log("pdc", (java.util.UUID) null, "pdc.skill_payload", Map.of(
                "operation", mutation.operation(),
                "item", itemStack == null ? "null" : itemStack.getType(),
                "amount", itemStack == null ? 0 : itemStack.getAmount(),
                "before", mutation.before().values(),
                "after", mutation.after().values(),
                "committed", mutation.committed(),
                "reason", mutation.reason()
        ));
    }
}
