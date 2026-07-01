package emaki.jiuwu.craft.codex.recipe;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.recipe.model.CodexRecipe;
import emaki.jiuwu.craft.codex.store.PlayerUnlockStore;

/**
 * Computes per-player recipe visibility following the plan's three-layer rule:
 * <pre>visible(player) = (default-unlock-all ? all : unlocked) ∪ whitelist − blacklist</pre>
 * Visibility only controls what a viewer shows; it never blocks actual crafting.
 *
 * <p>Blacklist and whitelist entries support a {@code namespace:*} wildcard to match
 * every recipe in a namespace.
 */
public final class RecipeVisibilityService {

    private final RecipeIndex recipeIndex;
    private final PlayerUnlockStore unlockStore;
    private volatile AppConfig config;

    public RecipeVisibilityService(RecipeIndex recipeIndex, PlayerUnlockStore unlockStore, AppConfig config) {
        this.recipeIndex = recipeIndex;
        this.unlockStore = unlockStore;
        this.config = config;
    }

    public void updateConfig(AppConfig config) {
        this.config = config;
    }

    /**
     * Tests a single recipe against the visibility rules for a player.
     *
     * @param player   the player uuid
     * @param recipeId the recipe id
     * @return whether the recipe should be visible
     */
    public boolean isVisible(UUID player, String recipeId) {
        if (recipeId == null) {
            return false;
        }
        AppConfig snapshot = config;
        if (matchesAny(recipeId, snapshot.globalBlacklist())) {
            return false;
        }
        if (matchesAny(recipeId, snapshot.unlockWhitelist())) {
            return true;
        }
        if (snapshot.defaultUnlockAll()) {
            return true;
        }
        return unlockStore != null && unlockStore.isUnlocked(player, recipeId);
    }

    /**
     * Computes the full set of recipe ids visible to a player.
     *
     * @param player the player uuid
     * @return the visible recipe id set (defensive copy)
     */
    public Set<String> visibleRecipeIds(UUID player) {
        AppConfig snapshot = config;
        Set<String> visible = new LinkedHashSet<>();
        for (CodexRecipe recipe : recipeIndex.all()) {
            String recipeId = recipe.recipeId();
            if (matchesAny(recipeId, snapshot.globalBlacklist())) {
                continue;
            }
            if (matchesAny(recipeId, snapshot.unlockWhitelist())
                    || snapshot.defaultUnlockAll()
                    || (unlockStore != null && unlockStore.isUnlocked(player, recipeId))) {
                visible.add(recipeId);
            }
        }
        return visible;
    }

    /** {@return whether the recipe is globally blacklisted regardless of player} */
    public boolean isBlacklisted(String recipeId) {
        return recipeId != null && matchesAny(recipeId, config.globalBlacklist());
    }

    private boolean matchesAny(String recipeId, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        String normalized = recipeId.toLowerCase(Locale.ROOT);
        String namespace = CodexRecipe.namespaceOf(recipeId);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String candidate = pattern.trim().toLowerCase(Locale.ROOT);
            if (candidate.endsWith(":*")) {
                String ns = candidate.substring(0, candidate.length() - 2);
                if (namespace.equals(ns)) {
                    return true;
                }
            } else if (candidate.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
