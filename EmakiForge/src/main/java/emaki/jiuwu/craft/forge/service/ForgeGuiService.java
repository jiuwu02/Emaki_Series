package emaki.jiuwu.craft.forge.service;

import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.ForgeGuiState;
import emaki.jiuwu.craft.forge.ForgeRuntimeSnapshot;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class ForgeGuiService {

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final GuiStateManager stateManager;
    private final ForgeGuiStateSupport stateSupport;
    private final ForgeGuiRenderer renderer;
    private final ForgeGuiInteractionController interactionController;

    public ForgeGuiService(EmakiForgePlugin plugin,
            GuiService guiService,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = new GuiStateManager();
        this.stateSupport = new ForgeGuiStateSupport();
        this.renderer = new ForgeGuiRenderer(plugin, stateSupport);
        this.interactionController = new ForgeGuiInteractionController(
                plugin,
                stateManager,
                stateSupport,
                renderer,
                executionDispatcher,
                threadOwnership);
    }

    public boolean openForgeGui(Player player, Recipe recipe) {
        return openForgeGui(player, recipe, plugin.runtimeSnapshot());
    }

    boolean openForgeGui(Player player, Recipe recipe, ForgeRuntimeSnapshot runtime) {
        if (player == null || runtime == null) {
            return false;
        }
        ForgeGuiState guiState = runtime.guiState();
        if (guiState != ForgeGuiState.READY) {
            if (runtime.messageService() != null) {
                runtime.messageService().send(player,
                        "forge.error.runtime." + guiState.name().toLowerCase(java.util.Locale.ROOT));
            }
            return false;
        }
        Recipe activeRecipe = recipe == null ? null : runtime.recipeLoader().get(recipe.id());
        if (recipe != null && activeRecipe == null) {
            runtime.messageService().send(player, "forge.error.runtime.stale_session");
            return false;
        }
        String templateId = stateSupport.resolveTemplateId(activeRecipe);
        var template = runtime.guiTemplateLoader().get(templateId);
        if (template == null || !isRuntimeCurrent(runtime)) {
            return false;
        }
        ForgeGuiSession state = new ForgeGuiSession(player, activeRecipe, templateId, runtime);
        stateSupport.refreshDerivedValues(state);
        if (!isRuntimeCurrent(runtime)) {
            return false;
        }
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                renderer.titleReplacements(state),
                runtime.itemIdentifierService()::createItem,
                (guiSession, slot) -> renderer.renderSlot(state, slot),
                interactionController.createSessionHandler(state)
        ));
        if (session == null) {
            return false;
        }
        if (!isRuntimeCurrent(runtime)
                || player.getOpenInventory().getTopInventory() != session.getInventory()) {
            player.closeInventory();
            return false;
        }
        state.setGuiSession(session);
        stateManager.put(state);
        if (!isRuntimeCurrent(runtime)
                || !stateManager.isCurrent(state)
                || player.getOpenInventory().getTopInventory() != session.getInventory()) {
            stateManager.remove(state);
            player.closeInventory();
            return false;
        }
        return true;
    }

    private boolean isRuntimeCurrent(ForgeRuntimeSnapshot runtime) {
        return runtime != null
                && plugin.runtimeSnapshot() == runtime
                && plugin.isGenerationActive(runtime.generation());
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

    public List<ForgeGuiSession> sessionsSnapshot() {
        return stateManager.snapshot();
    }

    public void settleShutdownSessionOnOwner(ForgeGuiSession session) {
        interactionController.settleShutdownSessionOnOwner(session);
    }

    public void handleShutdownClosureFailure(ForgeGuiSession session, String reason) {
        interactionController.handleShutdownClosureFailure(session, reason);
    }

    public void finalizeShutdownSessions(String reason) {
        for (ForgeGuiSession session : stateManager.snapshot()) {
            interactionController.abandonRetiredSession(session, reason);
        }
    }

    public void clearSettledSessions() {
        stateManager.clearSettled();
    }

    public void clearAllSessions() {
        stateManager.clear();
    }
}
