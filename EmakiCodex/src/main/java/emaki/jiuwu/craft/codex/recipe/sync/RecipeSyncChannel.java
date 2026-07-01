package emaki.jiuwu.craft.codex.recipe.sync;

import java.util.Set;

import org.bukkit.entity.Player;

/**
 * A single delivery mechanism that pushes a player's visible recipe set to their
 * client-side viewer. Implementations are probed at runtime; unavailable channels
 * report {@link #isAvailable()} as {@code false} and are skipped by the gateway.
 */
public interface RecipeSyncChannel {

    /** {@return a short channel id used in logs and debug output} */
    String id();

    /** {@return whether this channel can currently deliver (dependencies present, enabled)} */
    boolean isAvailable();

    /**
     * Pushes the given visible recipe ids to the player.
     *
     * @param player          the target online player
     * @param visibleRecipeIds the recipe ids that should be visible to the player
     */
    void sync(Player player, Set<String> visibleRecipeIds);

    /** Releases any channel-scoped resources (plugin-message registrations, etc.). */
    default void shutdown() {
    }
}
