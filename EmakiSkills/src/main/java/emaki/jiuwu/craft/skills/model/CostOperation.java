package emaki.jiuwu.craft.skills.model;

import java.util.Locale;

public enum CostOperation {

    CONSUME,
    REQUIRE;

    public static CostOperation fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "consume" -> CONSUME;
            case "require" -> REQUIRE;
            default -> null;
        };
    }
}
