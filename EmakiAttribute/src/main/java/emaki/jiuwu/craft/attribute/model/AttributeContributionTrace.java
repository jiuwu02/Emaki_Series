package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime-only diagnostic entry describing where one attribute value came from.
 */
public record AttributeContributionTrace(
        String attributeId,
        double value,
        String sourceModule,
        String sourceType,
        String sourceId,
        String sourceLabel,
        String slot,
        String itemId,
        String layer,
        boolean conditionPassed,
        String formula,
        double rawValue,
        double finalValue) {

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attributeId", safe(attributeId));
        result.put("value", value);
        result.put("sourceModule", safe(sourceModule));
        result.put("sourceType", safe(sourceType));
        result.put("sourceId", safe(sourceId));
        result.put("sourceLabel", safe(sourceLabel));
        result.put("slot", safe(slot));
        result.put("itemId", safe(itemId));
        result.put("layer", safe(layer));
        result.put("conditionPassed", conditionPassed);
        result.put("formula", safe(formula));
        result.put("rawValue", rawValue);
        result.put("finalValue", finalValue);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
