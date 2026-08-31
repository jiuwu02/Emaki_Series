package emaki.jiuwu.craft.corelib.condition;

public enum ConditionCombineMode {
    ALL_OF("all_of"),
    ANY_OF("any_of"),
    NONE_OF("none_of"),
    AT_LEAST("at_least"),
    EXACTLY("exactly");

    private final String configKey;

    ConditionCombineMode(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static ConditionCombineMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return ALL_OF;
        }
        String normalized = value.toLowerCase().trim();
        for (ConditionCombineMode mode : values()) {
            if (mode.configKey.equals(normalized)) {
                return mode;
            }
        }
        return ALL_OF;
    }
}
