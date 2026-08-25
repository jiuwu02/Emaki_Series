package emaki.jiuwu.craft.accessory.api.model;

import org.bukkit.inventory.ItemStack;

/**
 * Immutable view of one occupied accessory slot on one accessory page.
 *
 * <p>{@link #orphaned()} marks a slot that its own page no longer declares, which happens after a
 * server owner removes a part from the page or lowers its {@code count}. The item is still returned
 * so the player can take it back, but an orphaned slot grants no attributes and does not count
 * toward accessory set thresholds. A slot that belongs to a different page is <em>not</em> orphaned.
 *
 * @param pageId         the accessory page this slot belongs to
 * @param slotInstanceId the slot instance this item occupies
 * @param partId         the owning part id, or the raw prefix when the part no longer exists
 * @param item           a defensive copy of the stored item; never {@code null}
 * @param orphaned       whether the owning page no longer declares this slot
 * @since 1.0.3
 */
public record EquippedAccessoryView(String pageId,
        String slotInstanceId,
        String partId,
        ItemStack item,
        boolean orphaned) {

    /**
     * Canonical constructor; copies the item so callers cannot mutate stored state through the view.
     */
    public EquippedAccessoryView {
        pageId = pageId == null ? "" : pageId;
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
