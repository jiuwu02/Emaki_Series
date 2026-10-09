package emaki.jiuwu.craft.item.config;

public record ScriptSettings(boolean enabled, long timeoutMs) {

    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    public static ScriptSettings defaults() {
        return new ScriptSettings(true, DEFAULT_TIMEOUT_MS);
    }
}
