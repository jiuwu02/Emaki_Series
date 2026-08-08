package emaki.jiuwu.craft.strengthen.service;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;

public final class StrengthenGuiService implements Listener {

    private final EmakiStrengthenPlugin plugin;
    private final GuiService guiService;
    private final StrengthenGuiStateManager stateManager;
    private final StrengthenGuiRenderer renderer;
    private final StrengthenGuiInteractionController interactionController;
    private final ThreadOwnership threadOwnership;

    public StrengthenGuiService(EmakiStrengthenPlugin plugin,
            GuiService guiService,
            StrengthenAttemptService attemptService,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.threadOwnership = threadOwnership;
        this.stateManager = new StrengthenGuiStateManager();
        this.renderer = new StrengthenGuiRenderer(plugin, attemptService);
        this.interactionController = new StrengthenGuiInteractionController(plugin, stateManager, attemptService, renderer);
    }

    public boolean open(Player player) {
        if (player == null || plugin.attemptService() == null || !plugin.attemptService().accepting()
                || !threadOwnership.isEntityOwned(player)) {
            return false;
        }
        if (stateManager.hasPendingSettlement(player)) {
            interactionController.resumePendingSettlement(player);
            return false;
        }
        String templateId = resolveTemplateId(player);
        var template = plugin.guiTemplateLoader().get(templateId);
        if (template == null && !"strengthen_gui".equals(templateId)) {
            template = plugin.guiTemplateLoader().get("strengthen_gui");
        }
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event != null) {
            interactionController.resumePendingSettlement(event.getPlayer());
        }
    }

    public void clearAllSessions() {
        clearAllSessionsAsync().exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to close all strengthen GUI sessions: " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> clearAllSessionsAsync() {
        return guiService.closeAllAsync().whenComplete((_, _) -> stateManager.clear());
    }

    private String resolveTemplateId(Player player) {
        if (plugin.attemptService() == null) {
            return "strengthen_gui";
        }
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        AttemptPreview heldPreview = plugin.attemptService().preview(
                player, AttemptContext.of(heldItem, java.util.List.of()));
        if (heldPreview != null && heldPreview.recipe() != null) {
            String t = heldPreview.recipe().guiTemplate();
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        return "strengthen_gui";
    }
}
