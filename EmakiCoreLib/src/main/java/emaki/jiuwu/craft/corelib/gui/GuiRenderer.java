package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface GuiRenderer {

    ItemStack render(GuiSession session, GuiTemplate.ResolvedSlot slot);
}
