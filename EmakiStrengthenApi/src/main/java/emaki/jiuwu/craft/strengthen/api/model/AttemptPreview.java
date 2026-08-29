package emaki.jiuwu.craft.strengthen.api.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Non-committing projection of a strengthen attempt's cost and outcome.
 *
 * <p>Returned by {@code EmakiStrengthenApi#preview}. Reports whether the attempt
 * is eligible (and if not, an {@code errorKey}), the current and target star,
 * success rate, currency/material costs, the projected failure penalties, the
 * stat deltas a success would apply and which milestone stars it would unlock. Every collection component
 * is defensively copied and immutable.
 *
 * @param eligible           whether the attempt may proceed
 * @param errorKey           message key explaining ineligibility, may be
 *                           {@code null}
 * @param state              the current strengthen state
 * @param recipe             the matched recipe
 * @param currentStar        the item's current star level
 * @param targetStar         the star level a success would reach
 * @param successRate        success chance as a percentage
 * @param costs              currency costs of the attempt; never {@code null}
 * @param failureStar        the star level on failure
 * @param failureTemper      the temper level on failure
 * @param protectionApplied  whether failure protection is active
 * @param appliedTemperBonus temper bonus applied from materials
 * @param successDeltaStats  stat changes a success would apply; never
 *                           {@code null}
 * @param unlockingMilestones milestone stars unlocked on success; never
 *                           {@code null}
 * @param requiredMaterials  required material requirements; never {@code null}
 * @param optionalMaterials  optional material requirements; never {@code null}
 */
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

    /** Canonical constructor; defensively copies all collection fields. */
    public AttemptPreview {
        successRate = Double.isFinite(successRate) ? Math.max(0D, Math.min(100D, successRate)) : 0D;
        costs = costs == null ? List.of() : List.copyOf(costs);
        successDeltaStats = successDeltaStats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successDeltaStats));
        unlockingMilestones = unlockingMilestones == null ? Set.of() : Set.copyOf(unlockingMilestones);
        requiredMaterials = requiredMaterials == null ? List.of() : List.copyOf(requiredMaterials);
        optionalMaterials = optionalMaterials == null ? List.of() : List.copyOf(optionalMaterials);
    }

    /** {@return the summed amount across all currency costs} */
    public long totalCurrencyCost() {
        return costs.stream().mapToLong(AttemptCost::amount).sum();
    }
}
