package emaki.jiuwu.craft.item.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.item.model.SetBonusConfig;

public final class AppConfig extends BaseAppConfig {

    private final boolean releaseDefaultData;
    private final SetBonusConfig setBonus;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            SetBonusConfig setBonus) {
        super(language, configVersion, "2.4.10");
        this.releaseDefaultData = releaseDefaultData;
        this.setBonus = setBonus == null ? SetBonusConfig.defaults() : setBonus;
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", "2.4.10", true, SetBonusConfig.defaults());
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public SetBonusConfig setBonus() {
        return setBonus;
    }
}
