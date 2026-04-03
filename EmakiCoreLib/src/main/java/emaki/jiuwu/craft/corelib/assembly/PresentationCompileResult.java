package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;

public record PresentationCompileResult(List<EmakiPresentationEntry> entries,
                                        int nextSequence,
                                        List<PresentationCompileIssue> issues) {

    public PresentationCompileResult {
        List<EmakiPresentationEntry> normalizedEntries = new ArrayList<>();
        if (entries != null) {
            normalizedEntries.addAll(entries);
        }
        entries = normalizedEntries.isEmpty() ? List.of() : List.copyOf(normalizedEntries);

        List<PresentationCompileIssue> normalizedIssues = new ArrayList<>();
        if (issues != null) {
            normalizedIssues.addAll(issues);
        }
        issues = normalizedIssues.isEmpty() ? List.of() : List.copyOf(normalizedIssues);
    }

    public static PresentationCompileResult empty(int nextSequence) {
        return new PresentationCompileResult(List.of(), nextSequence, List.of());
    }
}
