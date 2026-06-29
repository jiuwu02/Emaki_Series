package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Backend-neutral view of a single GUI click.
 *
 * <p>Historically GUI handlers consumed Bukkit's {@link org.bukkit.event.inventory.InventoryClickEvent}
 * directly. That ties every handler to the entity-inventory (Bukkit) backend.
 * This context exposes only the operations the existing handlers actually use,
 * so the same handler code runs unchanged whether the menu is backed by a real
 * Bukkit inventory or a packet-driven virtual container.</p>
 *
 * <p>Slot positions inside the GUI are always addressed through the template
 * {@link GuiTemplate.ResolvedSlot} passed to the handler, never through a raw
 * protocol slot index; therefore this context intentionally does not expose a
 * raw-slot accessor.</p>
 *
 * <p>The "held item" abstraction unifies the three carry sources a vanilla
 * click can use: the cursor, a hotbar slot (number-key swap) and the off-hand
 * (swap-offhand). Handlers that simply read/write the cursor can keep using
 * {@link #cursorItem()} / {@link #setCursor(ItemStack)}; handlers that must also
 * honour number-key and off-hand swaps (currently only Forge) use
 * {@link #heldItem()} / {@link #setHeldItem(ItemStack)} which route to the
 * correct carry source for this click.</p>
 */
public interface GuiClickContext {

    /**
     * The player performing the click.
     */
    Player viewer();

    /**
     * Whether the clicked inventory is the top (GUI) inventory. When false the
     * player clicked their own inventory.
     */
    boolean isTopInventory();

    /**
     * The neutral click type used for slot click sounds (left/right/other).
     */
    GuiClickType clickType();

    boolean isShiftClick();

    boolean isLeftClick();

    boolean isRightClick();

    /**
     * Whether this click would transfer items out of the clicked slot in a way
     * the GUI cannot safely allow (shift move, collect-to-cursor, double click).
     * Mirrors the previous {@code GuiSessionHandler.isBlockedTransfer(event)}.
     */
    boolean isBlockedTransfer();

    /**
     * Whether this click specifically moves an item into the other inventory
     * (shift click / MOVE_TO_OTHER_INVENTORY). Forge uses this to decide whether
     * to run its shift-from-player-inventory transfer.
     */
    boolean isMoveToOtherInventory();

    /**
     * The item currently held on the cursor (clone-safe to mutate by callers
     * that previously mutated {@code event.getCursor()}). May be null/AIR.
     */
    ItemStack cursorItem();

    void setCursor(ItemStack item);

    /**
     * The item in the slot that was clicked (top or bottom inventory).
     */
    ItemStack currentItem();

    /**
     * The "held" item for this click, resolving number-key (hotbar) and
     * swap-offhand sources in addition to the plain cursor. Returns null when
     * the held source is empty, or when the click is an unsupported keyboard
     * action.
     */
    ItemStack heldItem();

    /**
     * Writes the held item back to the correct carry source for this click
     * (cursor, hotbar slot or off-hand).
     */
    void setHeldItem(ItemStack item);

    /**
     * Whether this is a keyboard click that the held-item abstraction does not
     * support (anything other than number-key and swap-offhand).
     */
    boolean isUnsupportedKeyboardClick();

    /**
     * Clears the slot the player clicked in the bottom (player) inventory.
     * Used by shift-transfer handlers to consume the source stack.
     */
    void clearClickedSlot();

    /**
     * Cancels the underlying interaction so the platform does not also act on
     * it. For the Bukkit backend this cancels the {@link org.bukkit.event.inventory.InventoryClickEvent}
     * (critical for player-inventory shift clicks, where the handler performs the
     * transfer manually and vanilla must not also move the item). For the packet
     * backend this is a no-op because the server is already authoritative and the
     * click was never applied client-side server-side.
     */
    void setCancelled(boolean cancelled);
}
