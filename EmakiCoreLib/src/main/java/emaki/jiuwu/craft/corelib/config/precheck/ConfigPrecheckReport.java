package emaki.jiuwu.craft.corelib.config.precheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public List<String> formatLines() {
        List<String> lines = new ArrayList<>();
        List<ConfigPrecheckIssue> issues = issues();
        lines.add("Config precheck " + (success() ? "passed" : "failed") + ": " + issues.size() + " issue(s).");
        for (ConfigPrecheckIssue issue : issues) {
            lines.add(issue.format());
        }
        if (issues.isEmpty()) {
            lines.add("[INFO] corelib -> no issues found.");
        }
        return List.copyOf(lines);
    }
}
