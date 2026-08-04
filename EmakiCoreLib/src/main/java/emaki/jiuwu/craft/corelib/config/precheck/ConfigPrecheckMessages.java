package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;

import emaki.jiuwu.craft.corelib.service.AbstractMessageService;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;

public final class ConfigPrecheckMessages {

    private static final String PASSED_KEY = "console.config_precheck_passed";
    private static final String FAILED_KEY = "console.config_precheck_failed";
    private static final String ISSUE_KEY = "console.config_precheck_issue";
    private static final String SEVERITY_PREFIX = "console.config_precheck.severity.";
    private static final String HINT_KEY = "console.config_precheck.hint";

    private ConfigPrecheckMessages() {
    }

    public static void logReport(LogMessages messages, String module, ConfigPrecheckReport report) {
        if (messages == null || report == null) {
            return;
        }
        Map<String, Object> summaryReplacements = summaryReplacements(module, report);
        if (report.success()) {
            messages.info(PASSED_KEY, summaryReplacements);
        } else {
            messages.warning(FAILED_KEY, summaryReplacements);
        }
        for (ConfigPrecheckIssue issue : report.issues()) {
            logIssue(messages, issue);
        }
    }

    public static void logIssue(LogMessages messages, ConfigPrecheckIssue issue) {
        if (messages == null || issue == null) {
            return;
        }
        Map<String, Object> replacements = issueReplacements(messages, issue);
        if (issue.severity().blocking()) {
            messages.severe(ISSUE_KEY, replacements);
        } else if (issue.severity() == ConfigPrecheckSeverity.WARN) {
            messages.warning(ISSUE_KEY, replacements);
        } else {
            messages.info(ISSUE_KEY, replacements);
        }
    }

    public static void sendReport(AbstractMessageService messages,
            CommandSender sender,
            String module,
            ConfigPrecheckReport report) {
        if (messages == null || sender == null || report == null) {
            return;
        }
        for (String line : formatReport(messages, module, report)) {
            messages.sendRaw(sender, line);
        }
    }

    public static List<String> formatReport(LogMessages messages, String module, ConfigPrecheckReport report) {
        if (messages == null || report == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add(messages.message(report.success() ? PASSED_KEY : FAILED_KEY, summaryReplacements(module, report)));
        for (ConfigPrecheckIssue issue : report.issues()) {
            lines.add(formatIssue(messages, issue));
        }
        return List.copyOf(lines);
    }

    public static String formatIssue(LogMessages messages, ConfigPrecheckIssue issue) {
        if (messages == null || issue == null) {
            return "";
        }
        return messages.message(ISSUE_KEY, issueReplacements(messages, issue));
    }

    private static Map<String, Object> summaryReplacements(String module, ConfigPrecheckReport report) {
        return Map.of(
                "module", Texts.isBlank(module) ? "corelib" : Texts.lower(module),
                "issues", report.issues().size()
        );
    }

    private static Map<String, Object> issueReplacements(LogMessages messages, ConfigPrecheckIssue issue) {
        return Map.of(
                "severity", messages.message(SEVERITY_PREFIX + Texts.lower(issue.severity().name())),
                "module", issue.module(),
                "path", Texts.isBlank(issue.path()) ? "-" : issue.path(),
                "message", issue.message(),
                "hint", formatHint(messages, issue.hint())
        );
    }

    private static String formatHint(LogMessages messages, String hint) {
        if (Texts.isBlank(hint)) {
            return "";
        }
        return messages.message(HINT_KEY, Map.of("hint", hint));
    }
}
