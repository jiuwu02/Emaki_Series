package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record AttemptPreview(boolean eligible,
        String errorKey,
        StrengthenState state,
        StrengthenProfile profile,
        int currentStar,
        int targetStar,
        double successRate,
        long currencyCost,
        int failureStar,
        int failureCrack,
        boolean protectionApplied,
        Map<String, Double> successDeltaStats,
        Set<Integer> unlockingMilestones,
        StrengthenMaterial baseMaterial,
        StrengthenMaterial supportMaterial,
        StrengthenMaterial protectionMaterial,
        StrengthenMaterial breakthroughMaterial) {

    public AttemptPreview {
        successDeltaStats = successDeltaStats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successDeltaStats));
        unlockingMilestones = unlockingMilestones == null ? Set.of() : Set.copyOf(unlockingMilestones);
    }
}
