package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.api.pdc.SkillPdcMutation;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridge;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;

final class StrengthenPdcAttributeWriter {

    private final EmakiStrengthenPlugin plugin;
    private final String sourceId;

    StrengthenPdcAttributeWriter(EmakiStrengthenPlugin plugin, String sourceId) {
        this.plugin = plugin;
        this.sourceId = sourceId;
    }

    void applyPdcAttributes(ItemStack itemStack, StrengthenRecipe recipe, StrengthenState state) {
        if (itemStack == null || recipe == null || state == null) {
            return;
        }
        observeSkillMutation(itemStack, EquipmentSkillPdcCodec.write(
                itemStack,
                recipe.cumulativeSkillIds(state.currentStar(), state.branchPath())
        ));
        StrengthenAttributeBridge gateway = plugin.pdcAttributeGateway();
        if (gateway == null || !gateway.available()) {
            return;
        }
        Map<String, Double> attributes = recipe.cumulativeAttributes(state.currentStar(), state.branchPath());
        if (attributes.isEmpty()) {
            gateway.clear(itemStack, sourceId);
            return;
        }
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("recipe_id", recipe.id());
        meta.put("current_star", String.valueOf(state.currentStar()));
        gateway.write(itemStack, sourceId, attributes, meta);
    }

    void clearPdcAttributes(ItemStack itemStack) {
        if (itemStack != null) {
            observeSkillMutation(itemStack, EquipmentSkillPdcCodec.clear(itemStack));
        }
        StrengthenAttributeBridge gateway = plugin.pdcAttributeGateway();
        if (gateway == null || !gateway.available() || itemStack == null) {
            return;
        }
        gateway.clear(itemStack, sourceId);
    }

    void preserveOtherAttributePayloads(ItemStack original, ItemStack rebuilt) {
        observeSkillMutation(rebuilt, EquipmentSkillPdcCodec.copy(original, rebuilt));
        StrengthenAttributeBridge gateway = plugin.pdcAttributeGateway();
        if (gateway == null || original == null || rebuilt == null) {
            return;
        }
        gateway.copyPayloads(original, rebuilt, Set.of(sourceId));
    }

    private void observeSkillMutation(ItemStack itemStack, SkillPdcMutation mutation) {
        if (plugin == null
                || plugin.debugLogger() == null
                || mutation == null
                || !plugin.debugLogger().shouldLog("pdc", (UUID) null)) {
            return;
        }
        plugin.debugLogger().log("pdc", (UUID) null, "pdc.skill_payload", Map.of(
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
