package emaki.jiuwu.craft.station.recipe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * Inverted index from material identity to the recipes that accept it.
 *
 * <p>Without this, every click would rescan the entire recipe library to find candidates. With it, only
 * recipes that accept at least one material the player actually has are ever scored, which keeps the
 * matcher's cost proportional to what is in front of the player rather than to how many recipes the
 * server defines.
 *
 * <p>Immutable once built. A reload builds a new index rather than mutating this one, so a session
 * holding the old index keeps reading a coherent snapshot.
 */
public final class RecipeIndex {

    private static final RecipeIndex EMPTY = new RecipeIndex(Map.of());

    private final Map<ItemSourceRef, Set<String>> bySource;

    private RecipeIndex(Map<ItemSourceRef, Set<String>> bySource) {
        this.bySource = bySource;
    }

    /** {@return an index containing no recipes} */
    public static RecipeIndex empty() {
        return EMPTY;
    }

    /**
     * Builds an index over a recipe collection.
     *
     * @param recipes the recipes to index; {@code null} entries are skipped
     * @return the index
     */
    public static RecipeIndex build(Collection<RecipeDefinition> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return EMPTY;
        }
        Map<ItemSourceRef, Set<String>> mapping = new LinkedHashMap<>();
        for (RecipeDefinition recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            for (MaterialRequirement requirement : recipe.requirements()) {
                for (ItemSourceRef source : requirement.sources()) {
                    mapping.computeIfAbsent(source, key -> new LinkedHashSet<>()).add(recipe.id());
                }
            }
        }
        Map<ItemSourceRef, Set<String>> frozen = new LinkedHashMap<>(mapping.size());
        mapping.forEach((source, ids) -> frozen.put(source, Set.copyOf(ids)));
        return new RecipeIndex(Map.copyOf(frozen));
    }

    /**
     * Finds every recipe that accepts at least one of the given materials.
     *
     * @param sources the materials in hand; {@code null} yields an empty set
     * @return the candidate recipe ids
     */
    public Set<String> candidates(Collection<ItemSourceRef> sources) {
        if (sources == null || sources.isEmpty() || bySource.isEmpty()) {
            return Set.of();
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (ItemSourceRef source : sources) {
            Set<String> ids = bySource.get(source);
            if (ids != null) {
                candidates.addAll(ids);
            }
        }
        return candidates;
    }

    /**
     * Restricts candidate lookup to one station's recipe set.
     *
     * @param sources   the materials in hand
     * @param allowed   the recipe ids the station permits; {@code null} means "no restriction"
     * @return the candidate recipe ids present in {@code allowed}
     */
    public Set<String> candidates(Collection<ItemSourceRef> sources, Set<String> allowed) {
        Set<String> candidates = candidates(sources);
        if (allowed == null || candidates.isEmpty()) {
            return candidates;
        }
        Set<String> restricted = new LinkedHashSet<>();
        for (String id : candidates) {
            if (allowed.contains(id)) {
                restricted.add(id);
            }
        }
        return restricted;
    }

    /** {@return how many distinct material identities are indexed} */
    public int indexedSourceCount() {
        return bySource.size();
    }

    /** {@return every indexed material identity} */
    public List<ItemSourceRef> indexedSources() {
        return List.copyOf(bySource.keySet());
    }
}
