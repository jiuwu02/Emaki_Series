package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

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
        ReflectivePdcAttributeGateway gateway = plugin.pdcAttributeGateway();
        if (gateway == null || !gateway.available()) {
            return;
        }
        Map<String, Double> eaAttributes = recipe.cumulativeEaAttributes(state.currentStar());
        if (eaAttributes.isEmpty()) {
            gateway.clear(itemStack, sourceId);
            return;
        }
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("recipe_id", recipe.id());
        meta.put("current_star", String.valueOf(state.currentStar()));
        gateway.write(itemStack, sourceId, eaAttributes, meta);
    }

    void clearPdcAttributes(ItemStack itemStack) {
        ReflectivePdcAttributeGateway gateway = plugin.pdcAttributeGateway();
        if (gateway == null || !gateway.available() || itemStack == null) {
            return;
        }
        gateway.clear(itemStack, sourceId);
    }

    void preserveOtherAttributePayloads(ItemStack original, ItemStack rebuilt) {
        ReflectivePdcAttributeGateway gateway = plugin.pdcAttributeGateway();
        if (gateway == null || original == null || rebuilt == null) {
            return;
        }
        gateway.copyPayloads(original, rebuilt, Set.of(sourceId));
    }
}
