package emaki.jiuwu.craft.corelib.config.precheck;

public record ConfigPrecheckFixResult(int fixed, int failed, ConfigPrecheckReport report) {

    public ConfigPrecheckFixResult {
        fixed = Math.max(0, fixed);
        failed = Math.max(0, failed);
        report = report == null ? ConfigPrecheckReport.empty() : report;
    }
}
