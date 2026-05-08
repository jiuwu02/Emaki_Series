package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashSet;
import java.util.Set;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.text.Texts;

public record StrengthenState(boolean eligible,
        String eligibleReason,
        boolean hasLayer,
        ItemSource baseSource,
        String baseSourceSignature,
        String recipeId,
        int currentStar,
        int crackLevel,
        Set<Integer> milestoneFlags,
        int successCount,
        int failureCount,
        long lastAttemptAt,
        String branchPath) {

    public StrengthenState {
        milestoneFlags = milestoneFlags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(milestoneFlags));
        branchPath = branchPath == null ? "" : branchPath;
    }

    /**
     * Backward-compatible constructor without branchPath (defaults to empty).
     */
    public StrengthenState(boolean eligible,
            String eligibleReason,
            boolean hasLayer,
            ItemSource baseSource,
            String baseSourceSignature,
            String recipeId,
            int currentStar,
            int crackLevel,
            Set<Integer> milestoneFlags,
            int successCount,
            int failureCount,
            long lastAttemptAt) {
        this(eligible, eligibleReason, hasLayer, baseSource, baseSourceSignature,
                recipeId, currentStar, crackLevel, milestoneFlags, successCount, failureCount, lastAttemptAt, "");
    }

    public static StrengthenState ineligible(String eligibleReason, ItemSource baseSource, String baseSourceSignature) {
        return new StrengthenState(false, eligibleReason, false, baseSource, baseSourceSignature, "", 0, 0, Set.of(), 0, 0, 0L, "");
    }

    public String profileId() {
        return recipeId;
    }

    public Set<Integer> firstReachFlags() {
        return milestoneFlags;
    }

    public int temperLevel() {
        return crackLevel;
    }

    public boolean hasBranch() {
        return Texts.isNotBlank(branchPath);
    }
}
