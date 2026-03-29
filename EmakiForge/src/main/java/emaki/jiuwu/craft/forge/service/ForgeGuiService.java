package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;
import org.bukkit.entity.Player;

public final class ForgeGuiService {

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final GuiStateManager stateManager;
    private final ForgeGuiStateSupport stateSupport;
    private final ForgeGuiRenderer renderer;
    private final ForgeGuiInteractionController interactionController;

    public ForgeGuiService(EmakiForgePlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = new GuiStateManager();
        this.stateSupport = new ForgeGuiStateSupport(plugin);
        this.renderer = new ForgeGuiRenderer(plugin, stateSupport);
        this.interactionController = new ForgeGuiInteractionController(plugin, stateManager, stateSupport, renderer);
    }

    public boolean openForgeGui(Player player, Recipe recipe) {
        if (player == null) {
            return false;
        }
        String templateId = stateSupport.resolveTemplateId(recipe);
        var template = stateSupport.prepareTemplate(recipe, templateId);
        if (template == null) {
            return false;
        }
        ForgeGuiSession state = new ForgeGuiSession(player, recipe, templateId);
        stateSupport.refreshDerivedValues(state);
        GuiSession session = guiService.open(new GuiOpenRequest(
            plugin,
            player,
            template,
            renderer.titleReplacements(state),
            plugin.itemIdentifierService()::createItem,
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

    public boolean openGeneralForgeGui(Player player) {
        return openForgeGui(player, null);
    }

    public ForgeGuiSession getSession(Player player) {
        return stateManager.get(player);
    }

    public void removeSession(Player player) {
        stateManager.remove(player);
    }

    public void clearAllSessions() {
        stateManager.clear();
    }
}
