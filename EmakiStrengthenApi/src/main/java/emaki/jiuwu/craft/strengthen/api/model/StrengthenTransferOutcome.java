package emaki.jiuwu.craft.strengthen.api.model;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

/**
 * Result of a completed strengthen-star transfer.
 *
 * @param resultItem the rebuilt target item carrying the transferred state
 * @param transferredStar the final star level after decay and event adjustment
 */
public record StrengthenTransferOutcome(ItemStack resultItem, int transferredStar) {

    /** Creates an immutable transfer outcome with a defensive item copy. */
    public StrengthenTransferOutcome {
        resultItem = Objects.requireNonNull(resultItem, "resultItem").clone();
        transferredStar = Math.max(0, transferredStar);
    }

    /** {@return a defensive copy of the rebuilt target item} */
    @Override
    public ItemStack resultItem() {
        return resultItem.clone();
    }
}
