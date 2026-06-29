package emaki.jiuwu.craft.corelib.action.loop;

public enum LoopTaskMode {
    REPLACE,
    REFRESH,
    IGNORE,
    ALLOW_DUPLICATE;

    public static LoopTaskMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return REPLACE;
        }
        try {
            return LoopTaskMode.valueOf(raw.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return REPLACE;
        }
    }
}
