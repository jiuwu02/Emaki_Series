package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record AttemptPreview(boolean eligible,
        String errorKey,
        StrengthenState state,
        StrengthenRecipe recipe,
        int currentStar,
        int targetStar,
        double successRate,
        List<AttemptCost> costs,
        int failureStar,
        int failureTemper,
        boolean protectionApplied,
        int appliedTemperBonus,
        Map<String, Double> successDeltaStats,
        Set<Integer> unlockingMilestones,
        List<AttemptMaterial> requiredMaterials,
        List<AttemptMaterial> optionalMaterials) {

    public AttemptPreview {
        costs = costs == null ? List.of() : List.copyOf(costs);
        successDeltaStats = successDeltaStats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successDeltaStats));
        unlockingMilestones = unlockingMilestones == null ? Set.of() : Set.copyOf(unlockingMilestones);
        requiredMaterials = requiredMaterials == null ? List.of() : List.copyOf(requiredMaterials);
        optionalMaterials = optionalMaterials == null ? List.of() : List.copyOf(optionalMaterials);
    }

    public long totalCurrencyCost() {
        return costs.stream().mapToLong(AttemptCost::amount).sum();
    }
}
