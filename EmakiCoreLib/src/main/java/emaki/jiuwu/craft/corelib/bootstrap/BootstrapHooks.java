package emaki.jiuwu.craft.corelib.bootstrap;

public interface BootstrapHooks {

    default void beforeBootstrap() {
    }

    default void afterBootstrap() {
    }

    default boolean shouldInstallDefaultData() {
        return true;
    }
}
