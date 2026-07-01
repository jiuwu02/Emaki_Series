package emaki.jiuwu.craft.codex.recipe.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.recipe.RecipeIndex;

/**
 * Channel 0: the native, zero-dependency baseline. It drives the vanilla recipe book
 * via {@link Player#discoverRecipes(java.util.Collection)} /
 * {@link Player#undiscoverRecipes(java.util.Collection)} so visible recipes appear and
 * hidden ones disappear in the player's recipe book. This guarantees the core
 * visibility semantics even without PacketEvents or a client mod.
 *
 * <p>All calls must run on the player's region/main thread; the gateway is responsible
 * for scheduling.
 */
public final class VanillaRecipeBookChannel implements RecipeSyncChannel {

    private final RecipeIndex recipeIndex;

    public VanillaRecipeBookChannel(RecipeIndex recipeIndex) {
        this.recipeIndex = recipeIndex;
    }

    @Override
    public String id() {
        return "vanilla_book";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void sync(Player player, Set<String> visibleRecipeIds) {
        if (player == null || visibleRecipeIds == null) {
            return;
        }
        List<NamespacedKey> discover = new ArrayList<>();
        List<NamespacedKey> undiscover = new ArrayList<>();
        for (String recipeId : recipeIndex.asMap().keySet()) {
            NamespacedKey key = NamespacedKey.fromString(recipeId);
            if (key == null) {
                continue;
            }
            if (visibleRecipeIds.contains(recipeId)) {
                discover.add(key);
            } else {
                undiscover.add(key);
            }
        }
        if (!discover.isEmpty()) {
            player.discoverRecipes(discover);
        }
        if (!undiscover.isEmpty()) {
            player.undiscoverRecipes(undiscover);
        }
    }
}
