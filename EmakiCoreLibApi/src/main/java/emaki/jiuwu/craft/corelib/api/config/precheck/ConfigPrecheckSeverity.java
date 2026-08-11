package emaki.jiuwu.craft.corelib.api.config.precheck;

public enum ConfigPrecheckSeverity {
    FATAL,
    ERROR,
    WARN,
    INFO;

    public boolean blocking() {
        return this == FATAL || this == ERROR;
    }
}
