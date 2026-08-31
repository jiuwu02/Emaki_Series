package emaki.jiuwu.craft.strengthen.api.model;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable read-only snapshot of mastery bound to one item instance.
 *
 * @param instanceId        stable item-instance identity; empty when the provider has not assigned one
 * @param currentExperience experience accumulated toward the next level
 * @param totalExperience   total experience accumulated by the item instance
 * @param level             current mastery level
 * @param softCap           configured soft level cap
 * @param milestones        milestone levels already reached
 * @param dataVersion       provider-owned mastery payload version
 */
public record ItemMasteryView(@NotNull String instanceId,
        double currentExperience,
        double totalExperience,
        int level,
        int softCap,
        @NotNull Set<Integer> milestones,
        int dataVersion) {

    public ItemMasteryView {
        instanceId = instanceId == null ? "" : instanceId;
        currentExperience = normalizeExperience(currentExperience);
        totalExperience = normalizeExperience(totalExperience);
        level = Math.max(0, level);
        softCap = Math.max(0, softCap);
        milestones = milestones == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(milestones));
        dataVersion = Math.max(0, dataVersion);
    }

    /** {@return whether this snapshot is bound to a stable item instance} */
    public boolean identified() {
        return !instanceId.isBlank();
    }

    private static double normalizeExperience(double experience) {
        return Double.isFinite(experience) ? Math.max(0.0D, experience) : 0.0D;
    }
}
