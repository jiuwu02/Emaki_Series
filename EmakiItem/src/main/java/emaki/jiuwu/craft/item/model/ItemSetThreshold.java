package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Map;

public record ItemSetThreshold(int requiredPieces,
        List<String> lore,
        Map<String, Double> attributes,
        List<String> skills) {

    public ItemSetThreshold {
        requiredPieces = Math.max(1, requiredPieces);
        lore = lore == null ? List.of() : List.copyOf(lore);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public boolean active(int equippedPieces) {
        return equippedPieces >= requiredPieces;
    }
}
