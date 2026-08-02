package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class ForgeLookupIndex {

    public record Snapshot(long generation,
            Map<String, ForgeMaterial> materialsBySource,
            Map<String, ForgeMaterial> materialsById,
            Map<String, BlueprintRequirement> blueprintsBySource,
            Map<String, List<Recipe>> recipesByConfiguredOutputSource,
            List<Recipe> genericRecipes,
            List<Recipe> sortedRecipes,
            int recipeCount,
            int keyCount,
            int sourceTypeCount,
            int invalidCount,
            int issueCount,
            long buildDurationNanos) {

        public Snapshot {
            materialsBySource = materialsBySource == null ? Map.of() : Map.copyOf(materialsBySource);
            materialsById = materialsById == null ? Map.of() : Map.copyOf(materialsById);
            blueprintsBySource = blueprintsBySource == null ? Map.of() : Map.copyOf(blueprintsBySource);
            recipesByConfiguredOutputSource = freezeRecipeIndex(recipesByConfiguredOutputSource);
            genericRecipes = genericRecipes == null ? List.of() : List.copyOf(genericRecipes);
            sortedRecipes = sortedRecipes == null ? List.of() : List.copyOf(sortedRecipes);
            buildDurationNanos = Math.max(0L, buildDurationNanos);
        }

        public static Snapshot empty(long generation) {
            return new Snapshot(generation, Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(),
                    0, 0, 0, 0, 0, 0L);
        }

        private static Map<String, List<Recipe>> freezeRecipeIndex(Map<String, List<Recipe>> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, List<Recipe>> frozen = new LinkedHashMap<>();
            for (Map.Entry<String, List<Recipe>> entry : source.entrySet()) {
                if (Texts.isBlank(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return frozen.isEmpty() ? Map.of() : Map.copyOf(frozen);
        }
    }

    public record Metrics(long generation, long hits, long misses) {
    }

    private static final class QueryCounters {
        private final long generation;
        private final LongAdder hits = new LongAdder();
        private final LongAdder misses = new LongAdder();

        private QueryCounters(long generation) {
            this.generation = generation;
        }
    }

    private final AtomicReference<Snapshot> active = new AtomicReference<>(Snapshot.empty(0L));
    private final AtomicReference<QueryCounters> queryCounters = new AtomicReference<>(new QueryCounters(0L));
    private final Supplier<Snapshot> activeSnapshotSupplier;

    public ForgeLookupIndex() {
        this(null);
    }

    public ForgeLookupIndex(Supplier<Snapshot> activeSnapshotSupplier) {
        this.activeSnapshotSupplier = activeSnapshotSupplier;
    }

    public Snapshot build(long generation, Map<String, Recipe> sourceRecipes, int invalidCount, int issueCount) {
        long started = System.nanoTime();
        Map<String, ForgeMaterial> materialIndex = new LinkedHashMap<>();
        Map<String, ForgeMaterial> materialIdIndex = new LinkedHashMap<>();
        Map<String, BlueprintRequirement> blueprintIndex = new LinkedHashMap<>();
        Map<String, List<Recipe>> outputRecipeIndex = new LinkedHashMap<>();
        List<Recipe> genericRecipeList = new ArrayList<>();
        List<Recipe> recipes = sourceRecipes == null
                ? new ArrayList<>()
                : new ArrayList<>(sourceRecipes.values());
        recipes.sort(Comparator.comparing(recipe -> Texts.lower(recipe.id())));
        Map<String, Boolean> sourceTypes = new LinkedHashMap<>();
        for (Recipe recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            ItemSourceRef outputSource = recipe.configuredOutputSource();
            recordSourceType(sourceTypes, outputSource);
            if (outputSource == null) {
                genericRecipeList.add(recipe);
            } else {
                outputRecipeIndex.computeIfAbsent(shorthand(outputSource), ignored -> new ArrayList<>()).add(recipe);
            }
            for (ForgeMaterial material : recipe.materials()) {
                if (material == null) {
                    continue;
                }
                recordSourceType(sourceTypes, material.source());
                String key = shorthand(material.source());
                if (!key.isBlank()) {
                    materialIndex.putIfAbsent(key, material);
                }
                String materialId = Texts.lower(material.key());
                if (!materialId.isBlank()) {
                    materialIdIndex.putIfAbsent(materialId, material);
                }
            }
            for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
                if (requirement == null) {
                    continue;
                }
                recordSourceType(sourceTypes, requirement.source());
                String key = shorthand(requirement.source());
                if (!key.isBlank()) {
                    blueprintIndex.putIfAbsent(key, requirement);
                }
            }
        }
        int keyCount = materialIndex.size() + materialIdIndex.size() + blueprintIndex.size()
                + outputRecipeIndex.size();
        return new Snapshot(
                generation,
                materialIndex,
                materialIdIndex,
                blueprintIndex,
                outputRecipeIndex,
                genericRecipeList,
                recipes,
                recipes.size(),
                keyCount,
                sourceTypes.size(),
                Math.max(0, invalidCount),
                Math.max(0, issueCount),
                System.nanoTime() - started
        );
    }

    public void install(Snapshot snapshot) {
        Snapshot next = snapshot == null ? Snapshot.empty(0L) : snapshot;
        active.set(next);
        queryCounters.set(new QueryCounters(next.generation()));
    }

    public Snapshot snapshot() {
        return activeSnapshot();
    }

    public Metrics metrics() {
        QueryCounters counters = queryCounters.get();
        return new Metrics(counters.generation, counters.hits.sum(), counters.misses.sum());
    }

    ForgeMaterial findMaterialBySource(ItemSourceRef source) {
        Snapshot snapshot = activeSnapshot();
        ForgeMaterial value = source == null ? null : snapshot.materialsBySource().get(shorthand(source));
        recordQuery(snapshot.generation(), value != null);
        return value;
    }

    ForgeMaterial findMaterialById(String materialId) {
        Snapshot snapshot = activeSnapshot();
        ForgeMaterial value = Texts.isBlank(materialId) ? null : snapshot.materialsById().get(Texts.lower(materialId));
        recordQuery(snapshot.generation(), value != null);
        return value;
    }

    BlueprintRequirement findBlueprintRequirementBySource(ItemSourceRef source) {
        Snapshot snapshot = activeSnapshot();
        BlueprintRequirement value = source == null ? null : snapshot.blueprintsBySource().get(shorthand(source));
        recordQuery(snapshot.generation(), value != null);
        return value;
    }

    List<Recipe> sortedRecipes() {
        return activeSnapshot().sortedRecipes();
    }

    List<Recipe> findRecipesByConfiguredOutputSource(ItemSourceRef source) {
        Snapshot snapshot = activeSnapshot();
        List<Recipe> value = source == null
                ? snapshot.genericRecipes()
                : snapshot.recipesByConfiguredOutputSource().getOrDefault(shorthand(source), List.of());
        recordQuery(snapshot.generation(), !value.isEmpty());
        return value;
    }

    List<Recipe> genericRecipes() {
        return activeSnapshot().genericRecipes();
    }

    private Snapshot activeSnapshot() {
        if (activeSnapshotSupplier != null) {
            Snapshot supplied = activeSnapshotSupplier.get();
            if (supplied != null) {
                return supplied;
            }
        }
        return active.get();
    }

    private void recordQuery(long generation, boolean hit) {
        QueryCounters counters = queryCounters.get();
        if (counters.generation != generation) {
            return;
        }
        if (hit) {
            counters.hits.increment();
        } else {
            counters.misses.increment();
        }
    }

    /**
     * Records which item source kinds a recipe set touches.
     *
     * <p>Only the resulting {@code size()} is ever read &mdash; it feeds the "distinct source kinds"
     * diagnostic counter. The key was the enum's {@code name()} and is now the kind's canonical key;
     * both are just de-duplication tokens that never leave this method's caller.
     */
    private static void recordSourceType(Map<String, Boolean> sink, ItemSourceRef source) {
        if (sink != null && source != null) {
            sink.put(source.kind().key(), Boolean.TRUE);
        }
    }

    private static String shorthand(ItemSourceRef source) {
        if (source == null) {
            return "";
        }
        String shorthand = Texts.lower(ItemSourceUtil.toShorthand(source));
        if (source.vanilla() && shorthand.startsWith("minecraft:")) {
            return shorthand.substring("minecraft:".length());
        }
        return shorthand;
    }
}
