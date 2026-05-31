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
 * Public API for inspecting and performing EmakiStrengthen item-strengthening
 * operations.
 *
 * <p>Registered with the Bukkit {@code ServicesManager} by EmakiStrengthen;
 * obtain it through {@link EmakiStrengthenApiProvider#get()}. Lets other plugins
 * read an item's strengthen state, preview an attempt's cost and outcome chances
 * without committing, perform an attempt, and rebuild an item's display layer.
 *
 * <p>All item arguments tolerate {@code null}.
 */
public interface EmakiStrengthenApi {

    /**
     * {@return whether the given item can be strengthened}
     *
     * @param itemStack the item to test; {@code null} yields {@code false}
     */
    boolean canStrengthen(@Nullable ItemStack itemStack);

    /**
     * Reads the current strengthen state of an item.
     *
     * @param itemStack the item to inspect; {@code null} yields an ineligible
     *                  state
     * @return the resolved state; never {@code null}
     */
    @NotNull
    StrengthenState readState(@Nullable ItemStack itemStack);

    /**
     * Computes a non-committing preview of a strengthen attempt.
     *
     * @param player  the player performing the attempt, may be {@code null}
     * @param context the attempt inputs (target item and materials), may be
     *                {@code null}
     * @return the preview describing cost, success rate and projected outcome;
     *         never {@code null}
     */
    @NotNull
    AttemptPreview preview(@Nullable Player player, @Nullable AttemptContext context);

    /**
     * Performs a strengthen attempt, consuming costs and materials.
     *
     * @param player  the player performing the attempt, may be {@code null}
     * @param context the attempt inputs, may be {@code null}
     * @return the result of the attempt; never {@code null}
     */
    @NotNull
    AttemptResult attempt(@Nullable Player player, @Nullable AttemptContext context);

    /**
     * Rebuilds the strengthen display layer (name/lore/stats) of an item from
     * its stored state.
     *
     * @param itemStack the item to rebuild; {@code null} yields {@code null}
     * @return the rebuilt item, or {@code null} when not applicable
     */
    @Nullable
    ItemStack rebuild(@Nullable ItemStack itemStack);
}
