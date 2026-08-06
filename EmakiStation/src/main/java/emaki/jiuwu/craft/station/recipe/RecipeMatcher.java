package emaki.jiuwu.craft.station.recipe;

import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * Coverage arithmetic for material requirements.
 *
 * <p>A requirement is satisfied by the combined count of every identity it accepts; which slot an item sits in
 * is never considered. There is no shaped matching, by design.
 *
 * <h2>What used to be here</h2>
 * This class also used to search a station's whole recipe set for "which recipe do these loose items make",
 * ranking ambiguous hits and offering the alternatives for cycling. That existed to serve input slots, where the
 * player put materials in and the station guessed their intent. A catalog station has no guessing to do: the
 * player names the recipe. The search, its ranking order, and the alternatives model are gone with it.
 *
 * <p><strong>Greedy allocation, no backtracking.</strong> When one identity could satisfy two different
 * requirements, it is allocated to whichever requirement declares it first. This can theoretically fail a recipe
 * that a full search would satisfy. That is a deliberate trade: backtracking over {@code long} amounts and
 * unbounded requirement counts has no predictable ceiling, whereas the greedy failure mode is both rare and
 * avoidable by an administrator splitting the recipe. The recipe configuration comments say so explicitly.
 */
public final class RecipeMatcher {

    private RecipeMatcher() {
    }

    /**
     * Tests whether the available materials cover a recipe at a given batch.
     *
     * @param recipe    the recipe to test
     * @param available the available counts per identity
     * @param batch     how many times the recipe would be applied
     * @return whether every requirement is covered
     */
    public static boolean supports(RecipeDefinition recipe,
            Map<ItemSourceRef, Long> available,
            long batch) {
        if (recipe == null || available == null) {
            return false;
        }
        Map<ItemSourceRef, Long> remaining = new java.util.LinkedHashMap<>(available);
        for (MaterialRequirement requirement : recipe.requirements()) {
            long needed = requirement.totalFor(batch);
            for (ItemSourceRef source : requirement.sources()) {
                if (needed <= 0L) {
                    break;
                }
                long have = remaining.getOrDefault(source, 0L);
                if (have <= 0L) {
                    continue;
                }
                long taken = Math.min(have, needed);
                remaining.put(source, have - taken);
                needed -= taken;
            }
            if (needed > 0L) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes how many batches the available materials support.
     *
     * <p>Each requirement is divided independently, which is exact under the greedy allocation above
     * whenever identities are not shared between requirements, and conservative when they are.
     *
     * @param recipe    the recipe to size
     * @param available the available counts per identity
     * @return the largest supported batch, possibly zero
     */
    public static long maxBatch(RecipeDefinition recipe, Map<ItemSourceRef, Long> available) {
        if (recipe == null || available == null || recipe.requirements().isEmpty()) {
            return 0L;
        }
        long best = Long.MAX_VALUE;
        for (MaterialRequirement requirement : recipe.requirements()) {
            long total = 0L;
            for (ItemSourceRef source : requirement.sources()) {
                long have = available.getOrDefault(source, 0L);
                if (have > 0L) {
                    total = saturatedAdd(total, have);
                }
            }
            long affordable = total / requirement.amount();
            if (affordable <= 0L) {
                return 0L;
            }
            best = Math.min(best, affordable);
        }
        return best == Long.MAX_VALUE ? 0L : best;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
