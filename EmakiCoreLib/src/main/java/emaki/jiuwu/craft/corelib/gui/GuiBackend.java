package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

/**
 * Strategy for how a {@link GuiSession} is presented to and synchronised with a
 * player.
 *
 * <p>CoreLib ships only the built-in backend:</p>
 * <ul>
 *   <li>{@link BukkitGuiBackend} — opens a real server-side inventory via
 *       {@code Player#openInventory} and relies on Bukkit inventory events.
 *       This is the default and the fallback.</li>
 * </ul>
 *
 * <p>Additional backends are provided by optional plugins and registered into
 * {@link GuiBackendRegistry} at runtime. For example EmakiGuiPacket registers a
 * {@code packet} backend that sends container packets directly (no server-side
 * container), so the same window can be re-sized in place without resetting the
 * cursor; it requires PacketEvents.</p>
 *
 * <p>A backend instance is shared across all plugins' {@link GuiService}s. It
 * therefore holds no per-plugin state; per-session routing is reached through
 * {@link GuiSession#registry()} carried by the session itself.</p>
 */
public interface GuiBackend {

    /**
     * Presents the session to its viewer for the first time using the supplied
     * pre-rendered slots (top-inventory slot index → item).
     */
    void open(GuiSession session, Map<Integer, ItemStack> renderedSlots);

    /**
     * Re-applies rendered slots to an already-open session without re-opening
     * the window, preserving the player's cursor.
     */
    void applySlots(GuiSession session, Map<Integer, ItemStack> renderedSlots);

    /**
     * Closes the session initiated by the server (menu switch, reload, explicit
     * close).
     */
    void close(GuiSession session);

    /**
     * Backend identifier, e.g. {@code "bukkit"} or {@code "packet"}.
     */
    String name();

    /**
     * Releases backend-wide resources (packet listeners, virtual windows).
     * Invoked when CoreLib disables.
     */
    default void shutdown() {
    }
}
