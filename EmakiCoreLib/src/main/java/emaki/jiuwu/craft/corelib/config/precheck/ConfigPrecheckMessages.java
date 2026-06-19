package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.Map;

import org.bukkit.command.CommandSender;

import emaki.jiuwu.craft.corelib.service.AbstractMessageService;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ConfigPrecheckMessages {

    private ConfigPrecheckMessages() {
    }

    public static void logReport(LogMessages messages, String module, ConfigPrecheckReport report) {
        if (messages == null || report == null) {
            return;
        }
        String moduleName = Texts.isBlank(module) ? "corelib" : Texts.lower(module);
        Map<String, Object> summaryReplacements = Map.of(
                "module", moduleName,
                "issues", report.issues().size()
        );
        if (report.success()) {
            messages.info("console.config_precheck_passed", summaryReplacements);
        } else {
            messages.warning("console.config_precheck_failed", summaryReplacements);
        }
        for (ConfigPrecheckIssue issue : report.issues()) {
            logIssue(messages, issue);
        }
    }

    public static void logIssue(LogMessages messages, ConfigPrecheckIssue issue) {
        if (messages == null || issue == null) {
            return;
        }
        Map<String, Object> replacements = issueReplacements(issue);
        if (issue.severity().blocking()) {
            messages.severe("console.config_precheck_issue", replacements);
        } else if (issue.severity() == ConfigPrecheckSeverity.WARN) {
            messages.warning("console.config_precheck_issue", replacements);
        } else {
            messages.info("console.config_precheck_issue", replacements);
        }
    }

    public static void sendReport(AbstractMessageService messages, CommandSender sender, String module, ConfigPrecheckReport report) {
        if (messages == null || sender == null || report == null) {
            return;
        }
        String moduleName = Texts.isBlank(module) ? "corelib" : Texts.lower(module);
        Map<String, Object> summaryReplacements = Map.of(
                "module", moduleName,
                "issues", report.issues().size()
        );
        messages.send(sender, report.success() ? "console.config_precheck_passed" : "console.config_precheck_failed", summaryReplacements);
        for (ConfigPrecheckIssue issue : report.issues()) {
            messages.send(sender, "console.config_precheck_issue", issueReplacements(issue));
        }
    }

    private static Map<String, Object> issueReplacements(ConfigPrecheckIssue issue) {
        return Map.of(
                "severity", issue.severity().name(),
                "module", issue.module(),
                "path", Texts.isBlank(issue.path()) ? "-" : issue.path(),
                "message", issue.message(),
                "hint", Texts.isBlank(issue.hint()) ? "" : " (" + issue.hint() + ")"
        );
    }
}
