package emaki.jiuwu.craft.attribute.service;

public record TraceSpec(
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
        String filter,
        double rawValue,
        double finalValue) {
}
