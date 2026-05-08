package emaki.jiuwu.craft.gem.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ResonanceEffects(
        List<String> actions,
        Map<String, Double> stats,
        List<String> skills,
        ResonanceNameModification nameModification,
        ResonanceLoreSection loreSection) {

    public ResonanceEffects {
        actions = actions == null ? List.of() : List.copyOf(actions);
        stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
