package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Backend-neutral view of a drag gesture across GUI slots.
 *
 * <p>Only Forge performs real single-slot drag placement; every other handler
 * simply cancels the drag. The accessors here cover exactly Forge's usage:
 * the raw drop slots, the resulting item per drop slot and the pre-drag cursor.
 * Raw slots are expressed in the same index space as
 * {@link GuiTemplate#resolvedSlotAt(int)} (top inventory slots first).</p>
 */
public interface GuiDragContext {

    Player viewer();

    /**
     * The raw slots the drag would deposit into.
     */
    Set<Integer> rawSlots();

    /**
     * The resulting item for each affected raw slot.
     */
    Map<Integer, ItemStack> newItems();

    /**
     * The cursor item as it was before the drag, used to deduct the placed
     * amount.
     */
    ItemStack oldCursor();

    void setCursor(ItemStack item);
}
