package emaki.jiuwu.craft.forge.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.forge.model.QualitySettings;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "3.3.0";

    private final boolean releaseDefaultData;
    private final QualitySettings qualitySettings;
    private final String defaultNumberFormat;
    private final String integerNumberFormat;
    private final String percentageNumberFormat;
    private final boolean opBypass;
    private final boolean invalidAsFailure;
    private final boolean historyEnabled;
    private final boolean historyAutoSave;
    private final int historySaveInterval;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            QualitySettings qualitySettings,
            String defaultNumberFormat,
            String integerNumberFormat,
            String percentageNumberFormat,
            boolean opBypass,
            boolean invalidAsFailure,
            boolean historyEnabled,
            boolean historyAutoSave,
            int historySaveInterval) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.qualitySettings = qualitySettings;
        this.defaultNumberFormat = defaultNumberFormat;
        this.integerNumberFormat = integerNumberFormat;
        this.percentageNumberFormat = percentageNumberFormat;
        this.opBypass = opBypass;
        this.invalidAsFailure = invalidAsFailure;
        this.historyEnabled = historyEnabled;
        this.historyAutoSave = historyAutoSave;
        this.historySaveInterval = historySaveInterval;
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                true,
                QualitySettings.defaults(),
                "0.##",
                "0",
                "0.##%",
                false,
                true,
                true,
                true,
                6000
        );
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public QualitySettings qualitySettings() {
        return qualitySettings;
    }

    public String defaultNumberFormat() {
        return defaultNumberFormat;
    }

    public String integerNumberFormat() {
        return integerNumberFormat;
    }

    public String percentageNumberFormat() {
        return percentageNumberFormat;
    }

    public boolean opBypass() {
        return opBypass;
    }

    public boolean invalidAsFailure() {
        return invalidAsFailure;
    }

    public boolean historyEnabled() {
        return historyEnabled;
    }

    public boolean historyAutoSave() {
        return historyAutoSave;
    }

    public int historySaveInterval() {
        return historySaveInterval;
    }
}
