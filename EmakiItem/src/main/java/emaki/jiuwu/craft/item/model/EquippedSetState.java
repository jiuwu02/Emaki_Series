package emaki.jiuwu.craft.item.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record EquippedSetState(ItemSetDefinition definition, Set<String> equippedPieces) {

    public EquippedSetState {
        equippedPieces = equippedPieces == null || equippedPieces.isEmpty() ? Set.of() : Set.copyOf(equippedPieces);
    }

    public int activeCount() {
        return equippedPieces.size();
    }

    public List<ItemSetThreshold> activeThresholds() {
        return definition == null ? List.of() : definition.activeThresholds(activeCount());
    }

    public Map<String, Double> mergedAttributes() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (ItemSetThreshold threshold : activeThresholds()) {
            threshold.attributes().forEach((key, value) -> result.merge(key, value, Double::sum));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    public List<String> mergedSkills() {
        Set<String> skills = new LinkedHashSet<>();
        for (ItemSetThreshold threshold : activeThresholds()) {
            skills.addAll(threshold.skills());
        }
        return skills.isEmpty() ? List.of() : List.copyOf(skills);
    }
}
