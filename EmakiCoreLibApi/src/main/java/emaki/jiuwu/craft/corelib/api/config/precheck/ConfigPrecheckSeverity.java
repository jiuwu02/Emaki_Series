package emaki.jiuwu.craft.corelib.api.config.precheck;

/**
 * Severity of one finding reported by the configuration precheck that validates module YAML
 * before it is applied.
 *
 * <p>{@link #blocking()} separates findings that make the checked configuration unusable
 * ({@link #FATAL}, {@link #ERROR}) from advisory findings ({@link #WARN}, {@link #INFO}) that are
 * reported for the operator without being marked blocking.
 */
public enum ConfigPrecheckSeverity {
    /** The configuration cannot be applied at all; the affected load path must stop. */
    FATAL,
    /** The entry is invalid and must be corrected before the configuration can be used. */
    ERROR,
    /** The entry is questionable but does not prevent the configuration from being used. */
    WARN,
    /** An informational note about the configuration; safe to ignore. */
    INFO;

    /**
     * {@return {@code true} for {@link #FATAL} and {@link #ERROR}, meaning the finding must stop
     * the checked configuration from being applied}
     */
    public boolean blocking() {
        return this == FATAL || this == ERROR;
    }
}
