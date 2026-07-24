package emaki.jiuwu.craft.corelib.debug;

public interface DebugLoggerProvider {

    DebugLogger debugLogger();

    default boolean globalDebugEnabled() {
        return DebugLogger.isGlobalAllEnabled();
    }

    default void setGlobalDebugEnabled(boolean enabled) {
        DebugLogger.setGlobalAllEnabled(enabled);
    }
}
