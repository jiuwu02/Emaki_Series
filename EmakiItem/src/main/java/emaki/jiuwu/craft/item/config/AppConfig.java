package emaki.jiuwu.craft.item.config;

import java.util.List;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.item.model.SetBonusConfig;

public final class AppConfig extends BaseAppConfig {

    private static final List<String> DEFAULT_MYTHIC_DROP_NAMES = List.of("emakiitem", "ei_item");

    private final boolean releaseDefaultData;
    private final SetBonusConfig setBonus;
    private final boolean mythicEnabled;
    private final boolean mythicDropsEnabled;
    private final List<String> mythicDropNames;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            SetBonusConfig setBonus) {
        this(language, configVersion, releaseDefaultData, setBonus, true, true, DEFAULT_MYTHIC_DROP_NAMES);
    }

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            SetBonusConfig setBonus,
            boolean mythicEnabled,
            boolean mythicDropsEnabled,
            List<String> mythicDropNames) {
        super(language, configVersion, "2.5.19");
        this.releaseDefaultData = releaseDefaultData;
        this.setBonus = setBonus == null ? SetBonusConfig.defaults() : setBonus;
        this.mythicEnabled = mythicEnabled;
        this.mythicDropsEnabled = mythicDropsEnabled;
        this.mythicDropNames = mythicDropNames == null || mythicDropNames.isEmpty()
                ? DEFAULT_MYTHIC_DROP_NAMES
                : List.copyOf(mythicDropNames);
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", "2.5.19", true, SetBonusConfig.defaults(),
                true, true, DEFAULT_MYTHIC_DROP_NAMES);
    }

    public static List<String> defaultMythicDropNames() {
        return DEFAULT_MYTHIC_DROP_NAMES;
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public SetBonusConfig setBonus() {
        return setBonus;
    }

    public boolean mythicEnabled() {
        return mythicEnabled;
    }

    public boolean mythicDropsEnabled() {
        return mythicDropsEnabled;
    }

    public List<String> mythicDropNames() {
        return mythicDropNames;
    }
}
