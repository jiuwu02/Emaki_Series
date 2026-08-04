package emaki.jiuwu.craft.cooking.service;

import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

final class GrinderSettings {

    private final Supplier<YamlSection> configuration;

    GrinderSettings(Supplier<YamlSection> configuration) {
        this.configuration = configuration;
    }

    boolean dropResult() {
        return configuration.get().getBoolean("stations.grinder.drop_result", true);
    }

    int checkDelayTicks() {
        return Math.max(1, configuration.get().getInt("stations.grinder.check_delay_ticks", 20));
    }
}
