package emaki.jiuwu.craft.station.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.MaterialAllocation;
import emaki.jiuwu.craft.corelib.matcher.MaterialAllocator;
import emaki.jiuwu.craft.corelib.matcher.MaterialRequest;

public final class RecipeMatcher {

    private static final int STACK_BATCH_CEILING = 100_000;

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

    public static MaterialAllocation allocate(RecipeDefinition recipe,
            List<ItemStack> stacks,
            Function<ItemStack, MatchContext> contextFactory,
            long batch) {
        if (recipe == null || stacks == null || contextFactory == null) {
            return MaterialAllocation.failure(List.of(), List.of());
        }
        List<MaterialRequest> requests = requestsOf(recipe, batch);
        if (requests == null) {
            return MaterialAllocation.failure(List.of(), List.of());
        }
        return MaterialAllocator.allocate(requests, stacks, contextFactory);
    }

    public static boolean supports(RecipeDefinition recipe,
            List<ItemStack> stacks,
            Function<ItemStack, MatchContext> contextFactory,
            long batch) {
        return allocate(recipe, stacks, contextFactory, batch).satisfied();
    }

    public static long maxBatch(RecipeDefinition recipe,
            List<ItemStack> stacks,
            Function<ItemStack, MatchContext> contextFactory,
            long ceiling) {
        if (recipe == null || stacks == null || contextFactory == null
                || recipe.requirements().isEmpty()) {
            return 0L;
        }
        List<MaterialRequest> requests = requestsOf(recipe, 1L);
        if (requests == null) {
            return 0L;
        }
        int cap = (int) Math.clamp(ceiling, 0L, STACK_BATCH_CEILING);
        return MaterialAllocator.maxBatch(requests, stacks, contextFactory, cap);
    }

    private static List<MaterialRequest> requestsOf(RecipeDefinition recipe, long batch) {
        List<MaterialRequest> requests = new ArrayList<>(recipe.requirements().size());
        for (MaterialRequirement requirement : recipe.requirements()) {
            long needed = requirement.totalFor(batch);
            if (needed > Integer.MAX_VALUE) {
                return null;
            }
            requests.add(new MaterialRequest(requirement.effectiveMatcher(), (int) needed));
        }
        return requests;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
