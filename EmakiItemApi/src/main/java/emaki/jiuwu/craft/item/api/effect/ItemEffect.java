package emaki.jiuwu.craft.item.api.effect;

import org.jetbrains.annotations.NotNull;

/**
 * A parsed custom effect that EmakiItem applies to an item while building it.
 *
 * <p>Returned by {@link ItemEffectType#parse(ItemEffectParseContext)}. EmakiItem always calls
 * {@link #clear(ItemEffectApplyContext)} first and {@link #apply(ItemEffectApplyContext)} second on the same
 * item, so {@code clear} is the place to remove data this effect may have written on a previous build and
 * {@code apply} is the place to (re)write it. Store your own data under your own namespace; never rewrite or
 * delete another module's persistent data.
 *
 * <p><strong>Threading:</strong> both callbacks run on the thread building the item. Do not block, and use the
 * item stack from the supplied context rather than retaining it.
 */
public interface ItemEffect {

    /**
     * Removes any data this effect previously wrote to the item.
     *
     * <p>Invoked immediately before {@link #apply(ItemEffectApplyContext)} on the same item. Implementations
     * must tolerate an item that carries no such data, which is the normal case for a freshly built stack.
     *
     * @param context the item being built and the definition context it was built from
     */
    default void clear(@NotNull ItemEffectApplyContext context) {
    }

    /**
     * Writes this effect's data to the item being built.
     *
     * @param context the item being built and the definition context it was built from
     */
    void apply(@NotNull ItemEffectApplyContext context);
}
