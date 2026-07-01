package emaki.jiuwu.craft.codex.recipe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import emaki.jiuwu.craft.codex.recipe.model.CodexRecipe;

/**
 * In-memory index of collected recipes. The whole map is replaced atomically on
 * reload so readers always observe a consistent snapshot without locking.
 */
public final class RecipeIndex {

    private final AtomicReference<Map<String, CodexRecipe>> recipes =
            new AtomicReference<>(Map.of());

    /**
     * Replaces the index contents with a fresh snapshot.
     *
     * @param collected the newly collected recipes
     */
    public void replaceAll(Map<String, CodexRecipe> collected) {
        recipes.set(collected == null ? Map.of() : Map.copyOf(collected));
    }

    /** Merges manual recipes on top of the current index (manual entries win on id clash). */
    public void merge(Map<String, CodexRecipe> manual) {
        if (manual == null || manual.isEmpty()) {
            return;
        }
        Map<String, CodexRecipe> merged = new LinkedHashMap<>(recipes.get());
        merged.putAll(manual);
        recipes.set(Map.copyOf(merged));
    }

    public CodexRecipe get(String recipeId) {
        return recipeId == null ? null : recipes.get().get(recipeId);
    }

    public boolean contains(String recipeId) {
        return recipeId != null && recipes.get().containsKey(recipeId);
    }

    public Collection<CodexRecipe> all() {
        return recipes.get().values();
    }

    public Map<String, CodexRecipe> asMap() {
        return recipes.get();
    }

    public int size() {
        return recipes.get().size();
    }
}
