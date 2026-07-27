package emaki.jiuwu.craft.storage.log;

import java.util.Locale;

/** Which surface triggered a storage operation. */
public enum StorageOperationSource {

    GUI("gui"),
    COMMAND("command"),
    API("api"),
    ACTION("action"),
    AUTO_PICKUP("auto_pickup");

    private final String id;

    StorageOperationSource(String id) {
        this.id = id;
    }

    /** {@return the stable lower-case id written to the log and matched against config} */
    public String id() {
        return id;
    }

    public static StorageOperationSource fromId(String raw, StorageOperationSource fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (StorageOperationSource source : values()) {
            if (source.id.equals(normalized)) {
                return source;
            }
        }
        return fallback;
    }
}
