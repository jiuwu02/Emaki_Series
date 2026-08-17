package emaki.jiuwu.craft.attribute.service;

import java.util.Map;

import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.registry.DerivedAttributeProvider;

final class AttributePowerProvider implements DerivedAttributeProvider {

    private final AttributeService service;

    AttributePowerProvider(AttributeService service) {
        this.service = service;
    }

    @Override
    public String attributeId() {
        return "attribute_power";
    }

    @Override
    public double compute(Map<String, Double> baseValues) {
        if (baseValues == null || baseValues.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (AttributeDefinition definition : service.registryService().attributeDefinitions()) {
            if (definition == null || "attribute_power".equals(definition.id())) {
                continue;
            }
            Double value = baseValues.get(definition.id());
            if (value == null) {
                continue;
            }
            double score = service.attributeBalanceRegistry() == null
                    ? definition.attributePower()
                    : service.attributeBalanceRegistry().scoreOf(definition.id(), definition.attributePower());
            total += value * score;
        }
        return Math.max(0D, total);
    }
}
