package emaki.jiuwu.craft.attribute.api.extension;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decides whether one equipped item may contribute anything to a player.
 *
 * <p>Unlike {@code AttributeContributionProvider}, which only adds values, a gate
 * can veto a whole item. Returning {@code false} drops that item's Lore and PDC
 * contributions together, so a failing gate cannot be bypassed by writing values
 * into Lore instead of PDC.
 *
 * <p>Implementations are consulted on hot equipment-collection paths and must be
 * cheap, side-effect free and safe to call for any item. In particular they must
 * not send messages, run actions or mutate the item.
 */
public interface ItemContributionGate {

    /** {@return the stable gate identifier, used for ordering and diagnostics} */
    @NotNull
    String id();

    /**
     * Returns whether the item is currently active for the player.
     *
     * <p>Gates that do not recognise an item must return {@code true} so unrelated
     * items keep working.
     *
     * @param player the owning player
     * @param itemStack the equipped item being collected
     * @param slotName the equipment slot name being collected, may be {@code null}
     * @return {@code false} to drop every contribution from this item
     */
    boolean isActive(@Nullable Player player, @Nullable ItemStack itemStack, @Nullable String slotName);
}
