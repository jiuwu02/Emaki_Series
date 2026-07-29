package emaki.jiuwu.craft.forge.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one blueprint item a forging recipe requires.
 *
 * @param itemId item source shorthand such as {@code minecraft-paper}
 * @param amount how many blueprint items the recipe consumes
 */
public record ForgeBlueprintView(@NotNull String itemId, int amount) {

    /**
     * Normalises the item id and clamps the amount so no accessor can return {@code null} or a
     * negative count.
     *
     * @param itemId item source shorthand
     * @param amount consumed amount
     */
    public ForgeBlueprintView {
        itemId = itemId == null ? "" : itemId;
        amount = Math.max(0, amount);
    }
}
