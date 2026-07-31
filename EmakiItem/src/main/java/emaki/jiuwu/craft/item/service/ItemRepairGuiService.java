package emaki.jiuwu.craft.item.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemRepairGuiService {

    private final EmakiItemPlugin plugin;
    private final GuiService guiService;
    private final ItemRepairGuiStateManager stateManager;
    private final ItemRepairGuiRenderer renderer;
    private final ItemRepairGuiInteractionController interactionController;

    public ItemRepairGuiService(EmakiItemPlugin plugin, GuiService guiService, ItemRepairService repairService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = new ItemRepairGuiStateManager();
        this.renderer = new ItemRepairGuiRenderer(plugin, repairService);
        this.interactionController = new ItemRepairGuiInteractionController(plugin, stateManager, repairService, renderer);
    }

    public boolean open(Player player) {
        if (player == null) {
            return false;
        }
        var template = plugin.guiTemplateLoader().get("repair_gui");
        if (template == null) {
            return false;
        }
        ItemRepairGuiSession state = new ItemRepairGuiSession(player);
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                java.util.Map.of(),
                (guiSession, slot) -> renderer.renderSlot(state, slot),
                interactionController.createSessionHandler(state)
        ));
        if (session == null) {
            return false;
        }
        state.setGuiSession(session);
        stateManager.put(state);
        return true;
    }

    public ItemRepairGuiSession getSession(Player player) {
        return stateManager.get(player);
    }

    public void clearAllSessions() {
        stateManager.clear();
    }
}
