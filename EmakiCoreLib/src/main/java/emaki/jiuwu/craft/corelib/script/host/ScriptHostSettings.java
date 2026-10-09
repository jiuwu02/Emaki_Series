package emaki.jiuwu.craft.corelib.script.host;

import org.jetbrains.annotations.NotNull;

public record ScriptHostSettings(boolean enabled, long timeoutMs) {

    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    public ScriptHostSettings {
        if (timeoutMs <= 0L) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
    }

    public static @NotNull ScriptHostSettings defaults() {
        return new ScriptHostSettings(true, DEFAULT_TIMEOUT_MS);
    }
}
