package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiRenderer;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

public final class GemGuiService {

    private final EmakiGemPlugin plugin;
    private final GuiService guiService;
    private final EmakiScheduling scheduling;
    private final GemGuiStateManager stateManager;
    private final GemGuiRenderer gemRenderer;
    private final GemOpenGuiRenderer openRenderer;
    private final GemGuiInteractionController gemInteractionController;
    private final GemOpenGuiInteractionController openInteractionController;

    public GemGuiService(EmakiGemPlugin plugin,
            GuiService guiService,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.scheduling = scheduling;
        this.stateManager = new GemGuiStateManager();
        this.gemRenderer = new GemGuiRenderer(plugin);
        this.openRenderer = new GemOpenGuiRenderer(plugin);
        this.gemInteractionController = new GemGuiInteractionController(plugin, stateManager, gemRenderer, this);
        this.openInteractionController = new GemOpenGuiInteractionController(plugin, stateManager, openRenderer, this);
    }

    public boolean open(Player player) {
        return open(player, plugin.appConfig().gui().defaultMode());
    }

    public boolean open(Player player, GemGuiMode mode) {
        return open(player, mode, null);
    }

    public boolean open(Player player, GemGuiMode mode, ItemStack initialItem) {
        if (player == null) {
            return false;
        }
        if (!scheduling.ownsEntity(player)) {
            plugin.getLogger().warning("Cannot open gem GUI outside player ownership: " + player.getUniqueId());
            return false;
        }
        return switch (normalizeMode(mode)) {
            case INLAY, UPGRADE, EXTRACT, REROLL_FULL, REROLL_VALUE -> openGem(player, mode, initialItem);
            case OPEN_SOCKET -> openSocket(player, initialItem);
        };
    }

    public boolean openSocket(Player player) {
        return openSocket(player, null);
    }

    public boolean openSocket(Player player, ItemStack initialTarget) {
        if (player == null) {
            return false;
        }
        if (!scheduling.ownsEntity(player)) {
            plugin.getLogger().warning("Cannot open socket GUI outside player ownership: " + player.getUniqueId());
            return false;
        }
        return openSocket(player, initialTarget, null);
    }

    public boolean switchTemplate(GemGuiSession state) {
        if (state == null) {
            return false;
        }
        state.setTemplateSwitching(true);
        boolean opened = openGem(state.player(), state.mode(), state.mutableTargetItem(), state);
        if (!opened) {
            state.setTemplateSwitching(false);
        }
        return opened;
    }

    boolean switchOpenTemplate(GemOpenGuiSession state) {
        if (state == null) {
            return false;
        }
        state.setTemplateSwitching(true);
        boolean opened = openSocket(state.player(), state.mutableTargetItem(), state);
        if (!opened) {
            state.setTemplateSwitching(false);
        }
        return opened;
    }

    private boolean openGem(Player player, GemGuiMode mode, ItemStack initialTarget) {
        return openGem(player, mode, initialTarget, null);
    }

    private boolean openGem(Player player, GemGuiMode mode, ItemStack initialTarget, GemGuiSession existingState) {
        if (player == null) {
            return false;
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(initialTarget);
        GuiTemplate template = GemGuiTemplates.resolveGemTemplate(plugin.guiTemplateLoader(), itemDefinition);
        if (template == null) {
            plugin.messageService().send(player, "gui.open_failed");
            return false;
        }
        GemGuiSession state = existingState == null ? new GemGuiSession(player) : existingState;
        state.setMode(normalizeGemMode(mode));
        state.setTargetItem(initialTarget);
        GuiSession session = openGui(player, template, (guiSession, slot) -> gemRenderer.renderSlot(state, slot),
                gemInteractionController.createSessionHandler(state));
        if (session == null) {
            return false;
        }
        state.setCurrentTemplateId(template.id());
        state.setGuiSession(session);
        stateManager.put(state);
        return true;
    }

    private boolean openSocket(Player player, ItemStack initialTarget, GemOpenGuiSession existingState) {
        if (player == null) {
            return false;
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(initialTarget);
        GuiTemplate template = GemGuiTemplates.resolveOpenTemplate(plugin.guiTemplateLoader(), itemDefinition);
        if (template == null) {
            plugin.messageService().send(player, "gui.open_socket_failed");
            return false;
        }
        GemOpenGuiSession state = existingState == null ? new GemOpenGuiSession(player) : existingState;
        state.setTargetItem(initialTarget);
        GuiSession session = openGui(player, template, (guiSession, slot) -> openRenderer.renderSlot(state, slot),
                openInteractionController.createSessionHandler(state));
        if (session == null) {
            return false;
        }
        state.setCurrentTemplateId(template.id());
        state.setGuiSession(session);
        stateManager.put(state);
        return true;
    }

    private GuiSession openGui(Player player,
            GuiTemplate template,
            GuiRenderer renderer,
            GuiSessionHandler handler) {
        return guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of(),
                renderer,
                handler
        ));
    }

    public GemGuiSession getSession(Player player) {
        return stateManager.getGem(player);
    }

    public GemOpenGuiSession getOpenSession(Player player) {
        return stateManager.getOpen(player);
    }

    public CompletableFuture<Void> clearAllSessionsAsync() {
        List<CompletableFuture<Void>> closes = new ArrayList<>();
        for (Player viewer : stateManager.viewers()) {
            if (viewer == null) {
                continue;
            }
            if (scheduling.ownsEntity(viewer)) {
                close(viewer);
                continue;
            }
            if (!viewer.isOnline()) {
                stateManager.remove(viewer);
                GuiSession session = guiService.getSession(viewer.getUniqueId());
                if (session != null) {
                    guiService.removeSession(viewer.getUniqueId(), session);
                }
                continue;
            }
            CompletableFuture<Void> closed = new CompletableFuture<>();
            closes.add(closed);
            try {
                scheduling.runForEntity(
                        plugin,
                        viewer,
                        () -> {
                            try {
                                close(viewer);
                                closed.complete(null);
                            } catch (Throwable throwable) {
                                closed.completeExceptionally(throwable);
                            }
                        },
                        () -> closed.completeExceptionally(new IllegalStateException(
                                "Viewer owner retired before gem session close: " + viewer.getUniqueId()))
                );
            } catch (Throwable throwable) {
                closed.completeExceptionally(throwable);
            }
        }
        return CompletableFuture.allOf(closes.toArray(CompletableFuture[]::new))
                .thenRun(stateManager::clear);
    }

    public void clearAllSessions() {
        clearAllSessionsAsync().join();
    }

    public void close(Player player) {
        if (player == null) {
            return;
        }
        if (!scheduling.ownsEntity(player) && player.isOnline()) {
            plugin.getLogger().warning("Cannot close gem GUI outside player ownership: " + player.getUniqueId());
            return;
        }
        guiService.close(player.getUniqueId());
        stateManager.remove(player);
    }

    private GemGuiMode normalizeMode(GemGuiMode mode) {
        return mode == null ? GemGuiMode.INLAY : mode;
    }

    private GemGuiMode normalizeGemMode(GemGuiMode mode) {
        GemGuiMode normalized = normalizeMode(mode);
        return normalized == GemGuiMode.OPEN_SOCKET ? GemGuiMode.INLAY : normalized;
    }
}
