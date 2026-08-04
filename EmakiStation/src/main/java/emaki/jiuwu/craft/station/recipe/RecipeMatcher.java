package emaki.jiuwu.craft.station.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.station.definition.StationRegistry;

/**
 * Unordered set matching of available materials against candidate recipes.
 *
 * <p>Matching never considers which slot an item sits in: a requirement is satisfied by the combined
 * count of every identity it accepts. There is no shaped matching, by design.
 *
 * <p><strong>Greedy allocation, no backtracking.</strong> When one identity could satisfy two different
 * requirements, it is allocated to whichever requirement declares it first. This can theoretically fail
 * a recipe that a full search would satisfy. That is a deliberate trade: backtracking over {@code long}
 * amounts and unbounded requirement counts has no predictable ceiling, whereas the greedy failure mode is
 * both rare and avoidable by an administrator splitting the recipe. The recipe configuration comments say
 * so explicitly.
 */
public final class RecipeMatcher {

    /**
     * Ranking order for ambiguous matches: more requirements first, then id ascending.
     *
     * <p>More requirements means more specific, so a recipe that consumes iron plus coal beats one that
     * only consumes iron when both are satisfiable. The id tiebreak keeps the result stable across
     * restarts, which matters because the winner is what the GUI preselects.
     */
    private static final Comparator<RecipeDefinition> RANKING =
            Comparator.<RecipeDefinition>comparingInt(recipe -> recipe.requirements().size())
                    .reversed()
                    .thenComparing(RecipeDefinition::id);

    private final StationRegistry registry;

    /**
     * Creates a matcher over one resolved registry.
     *
     * @param registry the registry to match against
     */
    public RecipeMatcher(StationRegistry registry) {
        this.registry = registry == null ? StationRegistry.empty() : registry;
    }

    /**
     * Matches available materials against one station's recipes.
     *
     * @param stationId the station whose recipe set bounds the search
     * @param available the available counts per identity
     * @return the best match, or {@link RecipeMatch#none()} when nothing is satisfiable
     */
    public RecipeMatch match(String stationId, Map<ItemSourceRef, Long> available) {
        if (available == null || available.isEmpty()) {
            return RecipeMatch.none();
        }
        Set<String> allowed = registry.recipeIdsOf(stationId);
        if (allowed.isEmpty()) {
            return RecipeMatch.none();
        }
        Set<String> candidateIds = registry.recipeIndex().candidates(available.keySet(), allowed);
        if (candidateIds.isEmpty()) {
            return RecipeMatch.none();
        }
        List<RecipeDefinition> satisfied = new ArrayList<>();
        for (String candidateId : candidateIds) {
            RecipeDefinition candidate = registry.recipe(candidateId);
            if (candidate != null && supports(candidate, available, 1L)) {
                satisfied.add(candidate);
            }
        }
        if (satisfied.isEmpty()) {
            return RecipeMatch.none();
        }
        satisfied.sort(RANKING);
        RecipeDefinition winner = satisfied.getFirst();
        return new RecipeMatch(winner, satisfied, maxBatch(winner, available));
    }

    /**
     * Tests whether the available materials cover a recipe at a given batch.
     *
     * @param recipe    the recipe to test
     * @param available the available counts per identity
     * @param batch     how many times the recipe would be applied
     * @return whether every requirement is covered
     */
    public boolean supports(RecipeDefinition recipe, Map<ItemSourceRef, Long> available, long batch) {
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
    public long maxBatch(RecipeDefinition recipe, Map<ItemSourceRef, Long> available) {
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
