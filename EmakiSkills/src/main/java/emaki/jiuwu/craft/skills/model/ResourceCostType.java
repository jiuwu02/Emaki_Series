package emaki.jiuwu.craft.skills.model;

public enum ResourceCostType {

    EA_RESOURCE,
    ATTRIBUTE_CHECK,
    LOCAL_RESOURCE;

    public static ResourceCostType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.strip().toLowerCase().replace('_', '-')) {
            case "ea-resource" -> EA_RESOURCE;
            case "attribute-check" -> ATTRIBUTE_CHECK;
            case "local-resource" -> LOCAL_RESOURCE;
            default -> null;
        };
    }
}
