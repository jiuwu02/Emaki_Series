package emaki.jiuwu.craft.item.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.item.model.ItemUpdateConfig;
import emaki.jiuwu.craft.item.model.SetBonusConfig;

public final class AppConfig extends BaseAppConfig {

    private final boolean releaseDefaultData;
    private final ItemUpdateConfig itemUpdate;
    private final SetBonusConfig setBonus;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            ItemUpdateConfig itemUpdate,
            SetBonusConfig setBonus) {
        super(language, configVersion, "1.0.0");
        this.releaseDefaultData = releaseDefaultData;
        this.itemUpdate = itemUpdate == null ? ItemUpdateConfig.defaults() : itemUpdate;
        this.setBonus = setBonus == null ? SetBonusConfig.defaults() : setBonus;
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", "1.0.0", true, ItemUpdateConfig.defaults(), SetBonusConfig.defaults());
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public ItemUpdateConfig itemUpdate() {
        return itemUpdate;
    }

    public SetBonusConfig setBonus() {
        return setBonus;
    }
}
