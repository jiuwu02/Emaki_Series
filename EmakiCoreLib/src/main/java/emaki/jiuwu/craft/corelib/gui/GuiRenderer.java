package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.inventory.ItemStack;

/**
 * Renders a single GUI slot.
 *
 * <p>Renderers may be executed by {@link AsyncGuiRenderer} on a worker thread.
 * Implementations must therefore treat rendering as a pure data-to-item
 * transformation: do not access Bukkit world, entity, online player state, or
 * other thread-affine server APIs from this method. If a renderer reuses an
 * existing {@link ItemStack}, return a clone or a freshly built stack so the GUI
 * session owns the mutable instance it displays.</p>
 */
@FunctionalInterface
public interface GuiRenderer {

    ItemStack render(GuiSession session, GuiTemplate.ResolvedSlot slot);
}
