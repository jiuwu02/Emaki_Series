package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;

public record VanillaAttributeModifierConfig(String attribute,
        Object amount,
        String operation,
        String slot,
        String name,
        boolean randomAmount) {

    public VanillaAttributeModifierConfig {
        attribute = attribute == null ? "" : attribute;
        amount = ConfigNodes.toPlainData(amount);
        operation = operation == null || operation.isBlank() ? "add_number" : operation;
        slot = slot == null || slot.isBlank() ? "any" : slot;
        name = name == null ? "" : name;
    }
}
