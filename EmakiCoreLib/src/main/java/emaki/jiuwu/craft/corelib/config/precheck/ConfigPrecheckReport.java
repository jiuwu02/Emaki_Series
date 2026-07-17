package emaki.jiuwu.craft.corelib.config.precheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.text.LogMessages;

public record ConfigPrecheckReport(
        Instant createdAt,
        List<ConfigPrecheckResult> results
) {

    public ConfigPrecheckReport {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static ConfigPrecheckReport empty() {
        return new ConfigPrecheckReport(Instant.now(), List.of());
    }

    public boolean success() {
        return issues().stream().noneMatch(issue -> issue.severity().blocking());
    }

    public List<ConfigPrecheckIssue> issues() {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        for (ConfigPrecheckResult result : results) {
            issues.addAll(result.issues());
        }
        return List.copyOf(issues);
    }

    public List<String> formatLines(LogMessages messages, String module) {
        return ConfigPrecheckMessages.formatReport(messages, module, this);
    }
}
