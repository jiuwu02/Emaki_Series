package emaki.jiuwu.craft.structure.template;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TemplateLoadReport {

    public record Problem(String source, String reasonKey, Map<String, Object> placeholders) {

        public Problem {
            placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        }
    }

    private final List<Problem> problems = new CopyOnWriteArrayList<>();

    public void recordProblem(String source, String reasonKey, Map<String, Object> placeholders) {
        problems.add(new Problem(source, reasonKey, placeholders));
    }

    public List<Problem> problems() {
        return List.copyOf(problems);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    public int problemCount() {
        return problems.size();
    }

    public void clear() {
        problems.clear();
    }
}
