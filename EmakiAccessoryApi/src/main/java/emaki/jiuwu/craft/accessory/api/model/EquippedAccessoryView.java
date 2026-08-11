package emaki.jiuwu.craft.accessory.api.model;

import org.bukkit.inventory.ItemStack;

/**
 * Immutable view of one occupied accessory slot.
 *
 * <p>{@link #orphaned()} marks a slot whose part no longer exists in the current configuration, which
 * happens after a server owner removes a part or lowers its {@code count}. The item is still returned
 * so the player can take it back, but an orphaned slot grants no attributes and does not count toward
 * accessory set thresholds.
 *
 * @param slotInstanceId the slot instance this item occupies
 * @param partId         the owning part id, or the raw prefix when the part no longer exists
 * @param item           a defensive copy of the stored item; never {@code null}
 * @param orphaned       whether the slot is no longer part of the active configuration
 */
public record EquippedAccessoryView(String slotInstanceId,
        String partId,
        ItemStack item,
        boolean orphaned) {

    /**
     * Canonical constructor; copies the item so callers cannot mutate stored state through the view.
     */
    public EquippedAccessoryView {
        slotInstanceId = slotInstanceId == null ? "" : slotInstanceId;
        partId = partId == null ? "" : partId;
        item = item == null ? null : item.clone();
    }

    /** {@return a defensive copy of the stored item, or {@code null} when the slot carried none} */
    @Override
    public ItemStack item() {
        return item == null ? null : item.clone();
    }
}
