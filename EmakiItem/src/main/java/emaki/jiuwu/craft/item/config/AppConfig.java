package emaki.jiuwu.craft.item.config;

import java.util.List;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.item.model.ItemDirectoryConfig;
import emaki.jiuwu.craft.item.model.ItemStateConfig;
import emaki.jiuwu.craft.item.model.ProficiencyGuardConfig;
import emaki.jiuwu.craft.item.model.SetBonusConfig;

public final class AppConfig extends BaseAppConfig {

    private static final List<String> DEFAULT_MYTHIC_DROP_NAMES = List.of("emakiitem", "ei_item");

    private final boolean releaseDefaultData;
    private final ItemDirectoryConfig directories;
    private final SetBonusConfig setBonus;
    private final boolean mythicEnabled;
    private final boolean mythicDropsEnabled;
    private final List<String> mythicDropNames;
    private final ItemStateConfig itemState;
    private final ProficiencyGuardConfig proficiencyGuard;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            SetBonusConfig setBonus) {
        this(language, configVersion, releaseDefaultData, ItemDirectoryConfig.defaults(), setBonus,
                true, true, DEFAULT_MYTHIC_DROP_NAMES,
                ItemStateConfig.defaults(), ProficiencyGuardConfig.defaults());
    }

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            ItemDirectoryConfig directories,
            SetBonusConfig setBonus,
            boolean mythicEnabled,
            boolean mythicDropsEnabled,
            List<String> mythicDropNames) {
        this(language, configVersion, releaseDefaultData, directories, setBonus,
                mythicEnabled, mythicDropsEnabled, mythicDropNames,
                ItemStateConfig.defaults(), ProficiencyGuardConfig.defaults());
    }

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            ItemDirectoryConfig directories,
            SetBonusConfig setBonus,
            boolean mythicEnabled,
            boolean mythicDropsEnabled,
            List<String> mythicDropNames,
            ItemStateConfig itemState,
            ProficiencyGuardConfig proficiencyGuard) {
        super(language, configVersion, "2.6.7");
        this.releaseDefaultData = releaseDefaultData;
        this.directories = directories == null ? ItemDirectoryConfig.defaults() : directories;
        this.setBonus = setBonus == null ? SetBonusConfig.defaults() : setBonus;
        this.mythicEnabled = mythicEnabled;
        this.mythicDropsEnabled = mythicDropsEnabled;
        this.mythicDropNames = mythicDropNames == null || mythicDropNames.isEmpty()
                ? DEFAULT_MYTHIC_DROP_NAMES
                : List.copyOf(mythicDropNames);
        this.itemState = itemState == null ? ItemStateConfig.defaults() : itemState;
        this.proficiencyGuard = proficiencyGuard == null
                ? ProficiencyGuardConfig.defaults()
                : proficiencyGuard;
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", "2.6.7", true, ItemDirectoryConfig.defaults(), SetBonusConfig.defaults(),
                true, true, DEFAULT_MYTHIC_DROP_NAMES,
                ItemStateConfig.defaults(), ProficiencyGuardConfig.defaults());
    }

    public ItemStateConfig itemState() {
        return itemState;
    }

    public ProficiencyGuardConfig proficiencyGuard() {
        return proficiencyGuard;
    }

    public static List<String> defaultMythicDropNames() {
        return DEFAULT_MYTHIC_DROP_NAMES;
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public ItemDirectoryConfig directories() {
        return directories;
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
