package emaki.jiuwu.craft.corelib.api;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Runtime compatibility result published by EmakiCoreLib.
 */
public record CompatibilityReport(
        boolean compatible,
        boolean verified,
        @NotNull String platform,
        @NotNull String minecraftVersion,
        @NotNull String javaVersion,
        int javaFeature,
        @NotNull String coreLibVersion,
        @NotNull String minimumMinecraftVersion,
        @NotNull String verifiedMaximumExclusive,
        int minimumJavaFeature,
        @NotNull List<Issue> issues) {

    public CompatibilityReport {
        platform = safe(platform);
        minecraftVersion = safe(minecraftVersion);
        javaVersion = safe(javaVersion);
        coreLibVersion = safe(coreLibVersion);
        minimumMinecraftVersion = safe(minimumMinecraftVersion);
        verifiedMaximumExclusive = safe(verifiedMaximumExclusive);
        javaFeature = Math.max(0, javaFeature);
        minimumJavaFeature = Math.max(0, minimumJavaFeature);
        issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
    }

    public static @NotNull CompatibilityReport unavailable() {
        return new CompatibilityReport(
                false,
                false,
                "UNAVAILABLE",
                "",
                System.getProperty("java.version", ""),
                Runtime.version().feature(),
                "",
                "",
                "",
                0,
                List.of(new Issue(Severity.ERROR, "REPORT_UNAVAILABLE", "Compatibility report is unavailable."))
        );
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.WARNING);
    }

    public @NotNull List<Issue> errors() {
        return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).toList();
    }

    public @NotNull List<Issue> warnings() {
        return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).toList();
    }

    public @NotNull String summary() {
        List<String> parts = new ArrayList<>();
        parts.add("compatible=" + compatible);
        parts.add("verified=" + verified);
        parts.add("platform=" + platform);
        parts.add("minecraft=" + minecraftVersion);
        parts.add("java=" + javaFeature);
        parts.add("corelib=" + coreLibVersion);
        parts.add("issues=" + issues.size());
        return String.join(", ", parts);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public record Issue(@NotNull Severity severity, @NotNull String code, @NotNull String message) {

        public Issue {
            severity = severity == null ? Severity.ERROR : severity;
            code = safe(code);
            message = safe(message);
        }
    }
}
