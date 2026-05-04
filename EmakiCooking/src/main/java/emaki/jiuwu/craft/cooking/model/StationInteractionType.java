package emaki.jiuwu.craft.cooking.model;

import java.util.Locale;

public enum StationInteractionType {
    LEFT_CLICK("left_click"),
    RIGHT_CLICK("right_click"),
    SHIFT_LEFT_CLICK("shift_left_click"),
    SHIFT_RIGHT_CLICK("shift_right_click");

    private final String configKey;

    StationInteractionType(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static StationInteractionType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        for (StationInteractionType type : values()) {
            if (type.configKey.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
