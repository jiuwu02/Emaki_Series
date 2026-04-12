package emaki.jiuwu.craft.strengthen.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

/**
 * Public API for querying and executing strengthen operations on item stacks.
 */
public interface EmakiStrengthenApi {

    /**
     * Returns whether the supplied item can currently enter the strengthen flow.
     *
     * @param itemStack the item to inspect
     * @return {@code true} when the item can be strengthened
     */
    boolean canStrengthen(@Nullable ItemStack itemStack);

    /**
     * Reads the current strengthen state from an item.
     *
     * @param itemStack the item to inspect
     * @return the resolved strengthen state
     */
    @NotNull
    StrengthenState readState(@Nullable ItemStack itemStack);

    /**
     * Calculates the preview result for a potential strengthen attempt.
     *
     * @param player the acting player, when available
     * @param context the attempt context containing target and material inputs
     * @return the computed preview
     */
    @NotNull
    AttemptPreview preview(@Nullable Player player, @Nullable AttemptContext context);

    /**
     * Executes a strengthen attempt.
     *
     * @param player the acting player, when available
     * @param context the attempt context containing target and material inputs
     * @return the strengthen result
     */
    @NotNull
    AttemptResult attempt(@Nullable Player player, @Nullable AttemptContext context);

    /**
     * Rebuilds an item's strengthen layer from stored state.
     *
     * @param itemStack the source item
     * @return the rebuilt item, or the original input when no rebuild is needed
     */
    @Nullable
    ItemStack rebuild(@Nullable ItemStack itemStack);
}
