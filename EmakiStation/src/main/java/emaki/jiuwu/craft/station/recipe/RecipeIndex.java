package emaki.jiuwu.craft.station.recipe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

public final class RecipeIndex {

    private static final RecipeIndex EMPTY = new RecipeIndex(Map.of());

    private final Map<ItemSourceRef, Set<String>> bySource;

    private RecipeIndex(Map<ItemSourceRef, Set<String>> bySource) {
        this.bySource = bySource;
    }

    public static RecipeIndex empty() {
        return EMPTY;
    }

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

    public int indexedSourceCount() {
        return bySource.size();
    }

    public List<ItemSourceRef> indexedSources() {
        return List.copyOf(bySource.keySet());
    }
}
