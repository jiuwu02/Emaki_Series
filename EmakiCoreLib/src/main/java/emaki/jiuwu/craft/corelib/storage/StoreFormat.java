package emaki.jiuwu.craft.corelib.storage;

import java.util.Locale;

public enum StoreFormat {

    YAML,
    BINARY,
    SQLITE;

    public static StoreFormat parse(String raw, StoreFormat fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "yaml" -> YAML;
            case "binary" -> BINARY;
            case "sqlite" -> SQLITE;
            default -> fallback;
        };
    }
}
