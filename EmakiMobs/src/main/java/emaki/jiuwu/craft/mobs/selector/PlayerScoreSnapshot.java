package emaki.jiuwu.craft.mobs.selector;

import java.util.Map;
import java.util.UUID;

public record PlayerScoreSnapshot(
        Map<String, Double> equipmentScores,
        Map<String, Double> expressionScores,
        Map<String, Boolean> filterResults,
        UUID worldId,
        double x,
        double y,
        double z,
        double health,
        long computedAtMillis
) {

    public PlayerScoreSnapshot {
        equipmentScores = equipmentScores == null ? Map.of() : Map.copyOf(equipmentScores);
        expressionScores = expressionScores == null ? Map.of() : Map.copyOf(expressionScores);
        filterResults = filterResults == null ? Map.of() : Map.copyOf(filterResults);
    }
}
