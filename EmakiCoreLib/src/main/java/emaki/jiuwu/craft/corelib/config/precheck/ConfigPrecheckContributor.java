package emaki.jiuwu.craft.corelib.config.precheck;

import emaki.jiuwu.craft.corelib.CoreLibConfig;

public interface ConfigPrecheckContributor {

    String module();

    ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context);

    default boolean supportsFix() {
        return false;
    }
}
