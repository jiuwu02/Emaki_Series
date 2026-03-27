package emaki.jiuwu.craft.attribute.api;

public record AttributeContribution(String attributeId, double value, String sourceId) {

    public AttributeContribution {
        sourceId = sourceId == null ? "" : sourceId;
    }
}
