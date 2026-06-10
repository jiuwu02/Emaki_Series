package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;

public record ItemSetThreshold(int requiredPieces,
        List<String> lore,
        Map<String, Double> attributes,
        List<String> skills,
        Object nameActions,
        Object loreActions,
        List<String> conditions) {

    public ItemSetThreshold(int requiredPieces,
            List<String> lore,
            Map<String, Double> attributes,
            List<String> skills) {
        this(requiredPieces, lore, attributes, skills, List.of(), List.of(), List.of());
    }

    public ItemSetThreshold {
        requiredPieces = Math.max(1, requiredPieces);
        lore = lore == null ? List.of() : List.copyOf(lore);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        skills = skills == null ? List.of() : List.copyOf(skills);
        nameActions = ConfigNodes.toPlainData(nameActions);
        loreActions = ConfigNodes.toPlainData(loreActions);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public boolean active(int equippedPieces) {
        return equippedPieces >= requiredPieces && conditions.isEmpty();
    }

    public boolean active(int equippedPieces, boolean conditionsMet) {
        return equippedPieces >= requiredPieces && conditionsMet;
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }
}
