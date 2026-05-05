package emaki.jiuwu.craft.corelib.bootstrap;

import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public interface BootstrapHooks {

    default void beforeBootstrap() {
    }

    default void afterBootstrap() {
    }

    default boolean shouldInstallDefaultData() {
        return true;
    }

    default void afterVersionedMerge(String relativePath, YamlSection runtime, YamlSection bundled) {
    }
}
