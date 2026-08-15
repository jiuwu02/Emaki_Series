package emaki.jiuwu.craft.accessory.model;

import java.util.List;
import java.util.Map;

public record AccessoryContributionSnapshot(Map<String, Double> attributes,
        Map<String, String> skills,
        Map<String, Integer> setPieceCount) {

    private static final AccessoryContributionSnapshot EMPTY =
            new AccessoryContributionSnapshot(Map.of(), Map.of(), Map.of());

    public AccessoryContributionSnapshot {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        skills = skills == null ? Map.of() : Map.copyOf(skills);
        setPieceCount = setPieceCount == null ? Map.of() : Map.copyOf(setPieceCount);
    }

    public static AccessoryContributionSnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return attributes.isEmpty() && skills.isEmpty();
    }

    public List<String> setIds() {
        return List.copyOf(setPieceCount.keySet());
    }
}
