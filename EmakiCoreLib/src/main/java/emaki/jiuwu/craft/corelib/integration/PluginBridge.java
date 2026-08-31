package emaki.jiuwu.craft.corelib.integration;

public interface PluginBridge {

    default boolean available() {
        return false;
    }

    default void syncRegistration(String sourceId) {
    }

    default void shutdown() {
    }
}
