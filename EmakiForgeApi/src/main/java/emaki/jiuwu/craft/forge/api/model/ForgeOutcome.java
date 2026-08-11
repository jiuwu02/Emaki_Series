package emaki.jiuwu.craft.forge.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Successful committed result of a programmatic forge operation.
 *
 * <p>A normal chance failure, validation rejection, event cancellation, runtime unavailability, or
 * execution error is represented by the outer
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult}; this value is created only after the
 * result item has been delivered and the runtime commit boundary has been crossed.
 *
 * @param recipeId   canonical recipe id
 * @param resultItem delivered forged item snapshot
 * @param quality    resolved quality tier id; empty when the recipe has no named tier
 * @param multiplier resolved quality multiplier
 */
@ApiStatus.Experimental
public record ForgeOutcome(@NotNull String recipeId,
                           @NotNull ItemStack resultItem,
                           @NotNull String quality,
                           double multiplier) {

    /** Normalises text fields and snapshots the delivered item. */
    public ForgeOutcome {
        recipeId = recipeId == null ? "" : recipeId;
        if (resultItem == null) {
            throw new NullPointerException("resultItem");
        }
        resultItem = resultItem.clone();
        quality = quality == null ? "" : quality;
    }

    /** {@return a clone of the delivered forged item snapshot} */
    @Override
    public @NotNull ItemStack resultItem() {
        return resultItem.clone();
    }
}
