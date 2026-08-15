package emaki.jiuwu.craft.station.recipe;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

public final class RecipeMatcher {

    private RecipeMatcher() {
    }

    public static boolean supports(RecipeDefinition recipe,
            Map<ItemSourceRef, Long> available,
            long batch) {
        if (recipe == null || available == null) {
            return false;
        }
        Map<ItemSourceRef, Long> remaining = new LinkedHashMap<>(available);
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
