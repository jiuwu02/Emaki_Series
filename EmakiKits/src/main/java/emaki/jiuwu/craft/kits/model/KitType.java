package emaki.jiuwu.craft.kits.model;

import java.util.Locale;

public enum KitType {
    JOIN,
    INVITE,
    DAILY,
    WEEKLY,
    MONTHLY,
    CDK,
    CHOICE,
    GENERIC;

    public static KitType fromConfig(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "join" -> JOIN;
            case "invite" -> INVITE;
            case "daily" -> DAILY;
            case "weekly" -> WEEKLY;
            case "monthly" -> MONTHLY;
            case "cdk" -> CDK;
            case "choice" -> CHOICE;
            case "generic" -> GENERIC;
            default -> null;
        };
    }
}
