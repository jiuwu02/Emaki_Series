package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashSet;
import java.util.Set;

import emaki.jiuwu.craft.corelib.item.ItemSource;

public record StrengthenState(boolean eligible,
        String eligibleReason,
        boolean hasLayer,
        ItemSource baseSource,
        String baseSourceSignature,
        String profileId,
        int currentStar,
        int crackLevel,
        Set<Integer> milestoneFlags,
        int successCount,
        int failureCount,
        long lastAttemptAt) {

    public StrengthenState {
        milestoneFlags = milestoneFlags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(milestoneFlags));
    }

    public static StrengthenState ineligible(String eligibleReason, ItemSource baseSource, String baseSourceSignature) {
        return new StrengthenState(false, eligibleReason, false, baseSource, baseSourceSignature, "", 0, 0, Set.of(), 0, 0, 0L);
    }
}
