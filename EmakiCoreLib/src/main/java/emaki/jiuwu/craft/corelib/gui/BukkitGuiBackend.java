package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

/**
 * Default {@link GuiBackend} that opens a real server-side inventory and lets
 * Bukkit inventory events drive interaction (handled by {@link GuiService}).
 *
 * <p>This backend is stateless and behaviourally identical to the pre-backend
 * implementation: {@link GuiSession} owns a real Bukkit {@code Inventory}, this
 * backend just applies items and calls {@code openInventory}/{@code closeInventory}.</p>
 */
public final class BukkitGuiBackend implements GuiBackend {

    @Override
    public void open(GuiSession session, Map<Integer, ItemStack> renderedSlots) {
        if (session == null || session.viewer() == null) {
            return;
        }
        session.applyRenderedSlots(renderedSlots);
        session.viewer().openInventory(session.getInventory());
    }

    @Override
    public void applySlots(GuiSession session, Map<Integer, ItemStack> renderedSlots) {
        if (session == null) {
            return;
        }
        session.applyRenderedSlots(renderedSlots);
    }

    @Override
    public void close(GuiSession session) {
        if (session != null && session.viewer() != null) {
            session.viewer().closeInventory();
        }
    }

    @Override
    public String name() {
        return "bukkit";
    }
}
