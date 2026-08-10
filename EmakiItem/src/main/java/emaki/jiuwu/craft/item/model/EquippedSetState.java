package emaki.jiuwu.craft.item.model;

import java.util.ArrayList;
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

    public Object mergedNameActions() {
        List<Object> actions = new ArrayList<>();
        for (ItemSetThreshold threshold : activeThresholds()) {
            appendActions(actions, threshold.nameActions());
        }
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
    }

    public Object mergedLoreActions() {
        List<Object> actions = new ArrayList<>();
        for (ItemSetThreshold threshold : activeThresholds()) {
            appendActions(actions, threshold.loreActions());
        }
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
    }

    private static void appendActions(List<Object> actions, Object raw) {
        if (actions == null || raw == null) {
            return;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    actions.add(entry);
                }
            }
            return;
        }
        actions.add(raw);
    }
}
