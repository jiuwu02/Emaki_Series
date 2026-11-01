package emaki.jiuwu.craft.corelib.api.furniture;

import java.util.UUID;
import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Normalised furniture event shared by all third-party furniture sources.
 *
 * <p>Both identity forms are carried: {@code rawId} keeps the source's own id verbatim (what
 * subscribers match multiblock members and build {@code ItemSourceRef} values from), while
 * {@link #furnitureId()} is the lower-cased {@code namespace:id} form used by reverse look-ups.
 *
 * @param type         the normalised event kind
 * @param source       which furniture source raised the event
 * @param rawId        the source's furniture id as-is, {@code null} when the event carried none
 * @param furnitureId  the lower-cased {@code namespace:id} form, {@code null} when unavailable
 * @param player       the acting player, {@code null} for place events without one
 * @param placerId     the placing player for {@link FurnitureEventType#PLACE}, otherwise {@code null}
 * @param anchor       the anchor block the furniture occupies
 * @param mainHand     whether the interaction used the main hand
 * @param leftClick    whether this counts as a left click
 * @param rightClick   whether this counts as a right click
 * @param cancelConsumer cancels the underlying Bukkit event; never {@code null}
 */
@ApiStatus.Experimental
public record FurnitureEvent(
        FurnitureEventType type,
        ItemSourceKind source,
        @Nullable String rawId,
        @Nullable String furnitureId,
        @Nullable Player player,
        @Nullable UUID placerId,
        Block anchor,
        boolean mainHand,
        boolean leftClick,
        boolean rightClick,
        Consumer<Boolean> cancelConsumer) {
}
