package emaki.jiuwu.craft.dummy.config;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record AppConfig(
        String language,
        String version,
        String connectionMode,
        String host,
        int port,
        String velocityHost,
        int velocityPort,
        int maxDummies,
        String namePrefix,
        int logRetentionDays,
        boolean perDummyLogFiles,
        boolean reconnectEnabled,
        int reconnectMaxAttempts,
        int reconnectDelayTicks,
        double movementStep,
        int movementIntervalTicks,
        int scenarioTimeoutSeconds,
        int scenarioPollIntervalTicks,
        List<String> scenarioAuditFiles
) {
    static final String CURRENT_VERSION = "1.0.0";

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                "direct",
                "127.0.0.1",
                44445,
                "127.0.0.1",
                44444,
                4,
                "dummy_",
                14,
                false,
                true,
                5,
                100,
                0.5D,
                2,
                300,
                10,
                List.of("plugins/EmakiDungeon/logs/audit-*.log")
        );
    }

    public static AppConfig parse(YamlSection section) {
        AppConfig defaults = defaults();
        List<String> auditFiles = section.getStringList("scenario.audit-files");
        if (auditFiles == null || auditFiles.isEmpty()) {
            auditFiles = defaults.scenarioAuditFiles();
        }
        return new AppConfig(
                section.getString("language", defaults.language()),
                section.getString("version", defaults.version()),
                section.getString("connection.mode", defaults.connectionMode()),
                section.getString("connection.host", defaults.host()),
                section.getInt("connection.port", defaults.port()),
                section.getString("connection.velocity-host", defaults.velocityHost()),
                section.getInt("connection.velocity-port", defaults.velocityPort()),
                section.getInt("limits.max-dummies", defaults.maxDummies()),
                section.getString("limits.name-prefix", defaults.namePrefix()),
                section.getInt("logs.retention-days", defaults.logRetentionDays()),
                section.getBoolean("logs.per-dummy-files", defaults.perDummyLogFiles()),
                section.getBoolean("reconnect.enabled", defaults.reconnectEnabled()),
                section.getInt("reconnect.max-attempts", defaults.reconnectMaxAttempts()),
                section.getInt("reconnect.delay-ticks", defaults.reconnectDelayTicks()),
                section.getDouble("movement.step", defaults.movementStep()),
                section.getInt("movement.interval-ticks", defaults.movementIntervalTicks()),
                section.getInt("scenario.default-timeout-seconds", defaults.scenarioTimeoutSeconds()),
                section.getInt("scenario.poll-interval-ticks", defaults.scenarioPollIntervalTicks()),
                auditFiles
        );
    }
}
