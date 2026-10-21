package emaki.jiuwu.craft.dummy.scenario;

import java.util.List;

public record ScenarioDefinition(
        String id,
        List<String> players,
        int timeoutSeconds,
        List<ScenarioStep> steps,
        String sourceFile
) {
}
