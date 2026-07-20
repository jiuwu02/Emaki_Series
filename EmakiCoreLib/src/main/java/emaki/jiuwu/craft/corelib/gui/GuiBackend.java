package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;






















public interface GuiBackend {





    void open(GuiSession session, Map<Integer, ItemStack> renderedSlots);





    void applySlots(GuiSession session, Map<Integer, ItemStack> renderedSlots);





    void close(GuiSession session);




    String name();


    default ConfiguredItemService configuredItemService() {
        return null;
    }





    default void shutdown() {
    }
}
