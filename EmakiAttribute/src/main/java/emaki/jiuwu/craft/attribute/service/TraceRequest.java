package emaki.jiuwu.craft.attribute.service;

public record TraceRequest(
        String attributeId,
        Double value,
        String sourceModule,
        String sourceType,
        String sourceId,
        String sourceLabel,
        String slot,
        String itemId,
        String layer,
        boolean conditionPassed,
        String formula,
        String filter) {
}
