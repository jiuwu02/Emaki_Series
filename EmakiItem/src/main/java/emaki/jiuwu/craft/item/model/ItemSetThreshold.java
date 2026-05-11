package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Map;

/**
 * A threshold level within an item set definition.
 * <p>
 * In addition to the piece count requirement, an optional list of conditions
 * can be specified. When conditions are present, all equipped set pieces must
 * satisfy the conditions (e.g. "strengthen_star >= 5") for this threshold to activate.
 */
public record ItemSetThreshold(int requiredPieces,
        List<String> lore,
        Map<String, Double> attributes,
        List<String> skills,
        List<String> conditions) {

    public ItemSetThreshold(int requiredPieces,
            List<String> lore,
            Map<String, Double> attributes,
            List<String> skills) {
        this(requiredPieces, lore, attributes, skills, List.of());
    }

    public ItemSetThreshold {
        requiredPieces = Math.max(1, requiredPieces);
        lore = lore == null ? List.of() : List.copyOf(lore);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        skills = skills == null ? List.of() : List.copyOf(skills);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public boolean active(int equippedPieces) {
        return equippedPieces >= requiredPieces && conditions.isEmpty();
    }

    /**
     * Check if this threshold is active considering both piece count and conditions.
     *
     * @param equippedPieces number of equipped set pieces
     * @param conditionsMet  whether all dynamic conditions are satisfied
     * @return true if the threshold should be activated
     */
    public boolean active(int equippedPieces, boolean conditionsMet) {
        return equippedPieces >= requiredPieces && conditionsMet;
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }
}
