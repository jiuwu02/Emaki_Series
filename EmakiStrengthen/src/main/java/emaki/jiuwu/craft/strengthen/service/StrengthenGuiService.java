package emaki.jiuwu.craft.strengthen.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenGuiService {

    private final EmakiStrengthenPlugin plugin;
    private final GuiService guiService;
    private final StrengthenGuiStateManager stateManager;
    private final StrengthenGuiRenderer renderer;
    private final StrengthenGuiInteractionController interactionController;

    public StrengthenGuiService(EmakiStrengthenPlugin plugin, GuiService guiService, StrengthenAttemptService attemptService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = new StrengthenGuiStateManager();
        this.renderer = new StrengthenGuiRenderer(plugin, attemptService);
        this.interactionController = new StrengthenGuiInteractionController(plugin, stateManager, attemptService, renderer);
    }

    public boolean open(Player player) {
        if (player == null) {
            return false;
        }
        var template = plugin.guiTemplateLoader().get("strengthen_gui");
        if (template == null) {
            plugin.messageService().send(player, "gui.open_failed");
            return false;
        }
        StrengthenGuiSession state = new StrengthenGuiSession(player);
        state.setPreview(plugin.attemptService().preview(player, state.toAttemptContext()));
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                java.util.Map.of(),
                (source, amount) -> plugin.coreItemFactory().create(source, amount),
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

    public StrengthenGuiSession getSession(Player player) {
        return stateManager.get(player);
    }

    public void clearAllSessions() {
        stateManager.clear();
    }
}
