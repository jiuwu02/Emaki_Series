package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashSet;
import java.util.Set;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Immutable snapshot of an item's strengthen progress as read from its stored
 * data.
 *
 * <p>Captures eligibility, the underlying base item source and signature, the
 * matched recipe id, current star and crack/temper levels, reached milestone
 * flags, success/failure counters, the selected branch path and the fracture
 * level.
 *
 * @param eligible            whether the item can be strengthened
 * @param eligibleReason      reason/message key when ineligible
 * @param hasLayer            whether a strengthen display layer is present
 * @param baseSource          the underlying base item source
 * @param baseSourceSignature a signature of the base source
 * @param recipeId            the matched recipe id
 * @param currentStar         the current star level
 * @param crackLevel          the current crack/temper level
 * @param milestoneFlags      milestone stars already reached; never {@code null}
 * @param successCount        total successful attempts
 * @param failureCount        total failed attempts
 * @param lastAttemptAt       epoch millis of the last attempt
 * @param branchPath          the selected branch path; never {@code null}
 * @param fractureLevel       the current fracture level; clamped to {@code >= 0}
 */
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
        String branchPath,
        int fractureLevel) {

    /**
     * Backwards-compatible constructor defaulting {@code fractureLevel} to 0.
     *
     * @param eligible            whether the item can be strengthened
     * @param eligibleReason      reason/message key when ineligible
     * @param hasLayer            whether a strengthen display layer is present
     * @param baseSource          the underlying base item source
     * @param baseSourceSignature a signature of the base source
     * @param recipeId            the matched recipe id
     * @param currentStar         the current star level
     * @param crackLevel          the current crack/temper level
     * @param milestoneFlags      milestone stars already reached
     * @param successCount        total successful attempts
     * @param failureCount        total failed attempts
     * @param lastAttemptAt       epoch millis of the last attempt
     * @param branchPath          the selected branch path
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
            long lastAttemptAt,
            String branchPath) {
        this(eligible, eligibleReason, hasLayer, baseSource, baseSourceSignature, recipeId,
                currentStar, crackLevel, milestoneFlags, successCount, failureCount, lastAttemptAt, branchPath, 0);
    }

    /** Canonical constructor; copies milestone flags and clamps fracture level. */
    public StrengthenState {
        milestoneFlags = milestoneFlags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(milestoneFlags));
        branchPath = branchPath == null ? "" : branchPath;
        fractureLevel = Math.max(0, fractureLevel);
    }

    /**
     * Builds an ineligible state carrying only the base source information.
     *
     * @param eligibleReason      reason/message key for ineligibility
     * @param baseSource          the underlying base item source
     * @param baseSourceSignature a signature of the base source
     * @return an ineligible {@link StrengthenState}
     */
    public static StrengthenState ineligible(String eligibleReason, ItemSource baseSource, String baseSourceSignature) {
        return new StrengthenState(false, eligibleReason, false, baseSource, baseSourceSignature, "", 0, 0, Set.of(), 0, 0, 0L, "", 0);
    }

    /** {@return the matched recipe id (alias of {@link #recipeId()})} */
    public String profileId() {
        return recipeId;
    }

    /** {@return the milestone stars already reached (alias of {@link #milestoneFlags()})} */
    public Set<Integer> firstReachFlags() {
        return milestoneFlags;
    }

    /** {@return the temper level (alias of {@link #crackLevel()})} */
    public int temperLevel() {
        return crackLevel;
    }

    /** {@return whether a non-blank branch path is selected} */
    public boolean hasBranch() {
        return Texts.isNotBlank(branchPath);
    }

    /** {@return whether the item currently has a fracture} */
    public boolean hasFracture() {
        return fractureLevel > 0;
    }
}
