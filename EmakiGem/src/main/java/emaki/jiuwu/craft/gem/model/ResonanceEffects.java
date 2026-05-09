package emaki.jiuwu.craft.gem.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;

public record ResonanceEffects(
        List<String> actions,
        Map<String, Double> stats,
        List<String> skills,
        Object nameActions,
        Object loreActions) {

    public ResonanceEffects {
        actions = actions == null ? List.of() : List.copyOf(actions);
        stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
        skills = skills == null ? List.of() : List.copyOf(skills);
        nameActions = ConfigNodes.toPlainData(nameActions);
        loreActions = ConfigNodes.toPlainData(loreActions);
    }
}
