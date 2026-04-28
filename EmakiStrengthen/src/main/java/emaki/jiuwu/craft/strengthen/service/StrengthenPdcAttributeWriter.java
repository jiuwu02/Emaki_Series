package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.integration.SkillPdcGateway;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

final class StrengthenPdcAttributeWriter {

    private final EmakiStrengthenPlugin plugin;
    private final String sourceId;
    private final SkillPdcGateway skillPdcGateway = new SkillPdcGateway();

    StrengthenPdcAttributeWriter(EmakiStrengthenPlugin plugin, String sourceId) {
        this.plugin = plugin;
        this.sourceId = sourceId;
    }

    void applyPdcAttributes(ItemStack itemStack, StrengthenRecipe recipe, StrengthenState state) {
        if (itemStack == null || recipe == null || state == null) {
            return;
        }
        skillPdcGateway.write(itemStack, recipe.cumulativeSkillIds(state.currentStar()));
        PdcAttributeGateway gateway = plugin.pdcAttributeGateway();
        if (gateway == null || !gateway.available()) {
            return;
        }
        Map<String, Double> attributes = recipe.cumulativeAttributes(state.currentStar());
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
            skillPdcGateway.clear(itemStack);
        }
        PdcAttributeGateway gateway = plugin.pdcAttributeGateway();
        if (gateway == null || !gateway.available() || itemStack == null) {
            return;
        }
        gateway.clear(itemStack, sourceId);
    }

    void preserveOtherAttributePayloads(ItemStack original, ItemStack rebuilt) {
        skillPdcGateway.copy(original, rebuilt);
        PdcAttributeGateway gateway = plugin.pdcAttributeGateway();
        if (gateway == null || original == null || rebuilt == null) {
            return;
        }
        gateway.copyPayloads(original, rebuilt, Set.of(sourceId));
    }
}
