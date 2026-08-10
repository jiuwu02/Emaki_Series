package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;

import emaki.jiuwu.craft.corelib.service.AbstractMessageService;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;

public final class ConfigPrecheckMessages {

    private static final String PASSED_KEY = "console.config_precheck_passed";
    private static final String FAILED_KEY = "console.config_precheck_failed";
    private static final String ISSUE_KEY = "console.config_precheck_issue";
    private static final String ISSUE_WARN_KEY = "console.config_precheck_issue_warn";
    private static final String ISSUE_ERROR_KEY = "console.config_precheck_issue_error";
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
        String key = issueKey(messages, issue.severity());
        if (issue.severity().blocking()) {
            messages.severe(key, replacements);
        } else if (issue.severity() == ConfigPrecheckSeverity.WARN) {
            messages.warning(key, replacements);
        } else {
            messages.info(key, replacements);
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
        return messages.message(issueKey(messages, issue.severity()), issueReplacements(messages, issue));
    }

    /**
     * Picks the line template that matches the severity, so an administrator can spot a blocking issue by
     * colour alone instead of reading every line.
     *
     * <p>Falls back to the plain {@link #ISSUE_KEY} template when a deployed language file predates the
     * per-severity keys: a missing key resolves to the key itself, and printing {@code
     * console.config_precheck_issue_error} would be worse than printing an uncoloured line.
     */
    private static String issueKey(LogMessages messages, ConfigPrecheckSeverity severity) {
        if (severity == ConfigPrecheckSeverity.INFO) {
            return ISSUE_KEY;
        }
        String key = severity.blocking() ? ISSUE_ERROR_KEY : ISSUE_WARN_KEY;
        String resolved = messages.message(key);
        return Texts.isBlank(resolved) || key.equals(resolved.trim()) ? ISSUE_KEY : key;
    }

    private static Map<String, Object> summaryReplacements(String module, ConfigPrecheckReport report) {
        long count = report.success()
                ? (long) report.issues().size()
                : report.issues().stream().filter(i -> i.severity().blocking()).count();
        return Map.of(
                "module", Texts.isBlank(module) ? "corelib" : Texts.lower(module),
                "issues", count
        );
    }

    private static Map<String, Object> issueReplacements(LogMessages messages, ConfigPrecheckIssue issue) {
        return Map.of(
                "severity", messages.message(SEVERITY_PREFIX + Texts.lower(issue.severity().name())),
                "module", issue.module(),
                "path", Texts.isBlank(issue.path()) ? "-" : issue.path(),
                "message", issueBody(issue),
                "hint", formatHint(messages, issue.hint())
        );
    }

    /**
     * Strips formatting from a warning or blocking issue body so the severity colour applies to the whole
     * line.
     *
     * <p>Issue bodies arrive already localized, and a module's own wording often carries its own colour (for
     * example {@code station.recipe_bad_source} is wrapped in {@code <yellow>}). Left in place, that inner
     * colour would win over the outer red and defeat the point of colouring by severity. Stripping happens
     * here rather than in the language files because those same keys are also logged on their own at load
     * time, where their colour is still wanted. INFO bodies are left untouched to keep passing lines looking
     * exactly as they did before.
     */
    private static String issueBody(ConfigPrecheckIssue issue) {
        if (issue.severity() == ConfigPrecheckSeverity.INFO) {
            return issue.message();
        }
        String plain = MiniMessages.plainText(issue.message());
        return Texts.isBlank(plain) ? issue.message() : plain;
    }

    private static String formatHint(LogMessages messages, String hint) {
        if (Texts.isBlank(hint)) {
            return "";
        }
        return messages.message(HINT_KEY, Map.of("hint", hint));
    }
}
