package emaki.jiuwu.craft.corelib.config.precheck;

import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;

public record ConfigPrecheckIssue(
        String module,
        String path,
        ConfigPrecheckSeverity severity,
        String message,
        String hint,
        @Nullable ConfigPrecheckFixAction fix
) {

    public ConfigPrecheckIssue {
        module = Texts.isBlank(module) ? "corelib" : Texts.lower(module);
        path = Texts.toStringSafe(path);
        severity = severity == null ? ConfigPrecheckSeverity.INFO : severity;
        message = Texts.toStringSafe(message);
        hint = Texts.toStringSafe(hint);
    }

    public static ConfigPrecheckIssue of(String module, String path, ConfigPrecheckSeverity severity, String message) {
        return new ConfigPrecheckIssue(module, path, severity, message, "", null);
    }

    public static ConfigPrecheckIssue fixable(String module,
            String path,
            ConfigPrecheckSeverity severity,
            String message,
            ConfigPrecheckFixAction fix) {
        return new ConfigPrecheckIssue(module, path, severity, message, "", fix);
    }

    public String format(LogMessages messages) {
        return ConfigPrecheckMessages.formatIssue(messages, this);
    }
}
