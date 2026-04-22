package emaki.jiuwu.craft.skills.model;

public enum CostOperation {

    CONSUME,
    REQUIRE;

    public static CostOperation fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.strip().toLowerCase()) {
            case "consume" -> CONSUME;
            case "require" -> REQUIRE;
            default -> null;
        };
    }
}
