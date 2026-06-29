package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.List;

public record ConfigPrecheckResult(
        String module,
        List<ConfigPrecheckIssue> issues
) {

    public ConfigPrecheckResult {
        module = module == null || module.isBlank() ? "corelib" : module;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean success() {
        return issues.stream().noneMatch(issue -> issue.severity().blocking());
    }
}
