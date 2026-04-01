package emaki.jiuwu.craft.forge.config;

import emaki.jiuwu.craft.forge.model.QualitySettings;

public final class AppConfig {

    private final String language;
    private final String configVersion;
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
        this.language = language;
        this.configVersion = configVersion;
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
                "1.1",
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

    public String language() {
        return language;
    }

    public String configVersion() {
        return configVersion;
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
