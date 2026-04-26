package emaki.jiuwu.craft.gem.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiRenderer;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

public final class GemGuiService {

    private final EmakiGemPlugin plugin;
    private final GuiService guiService;
    private final GemGuiStateManager stateManager;
    private final GemGuiRenderer gemRenderer;
    private final GemOpenGuiRenderer openRenderer;
    private final GemUpgradeGuiRenderer upgradeRenderer;
    private final GemGuiInteractionController gemInteractionController;
    private final GemOpenGuiInteractionController openInteractionController;
    private final GemUpgradeGuiInteractionController upgradeInteractionController;

    public GemGuiService(EmakiGemPlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = new GemGuiStateManager();
        this.gemRenderer = new GemGuiRenderer(plugin);
        this.openRenderer = new GemOpenGuiRenderer(plugin);
        this.upgradeRenderer = new GemUpgradeGuiRenderer(plugin);
        this.gemInteractionController = new GemGuiInteractionController(plugin, stateManager, gemRenderer, this);
        this.openInteractionController = new GemOpenGuiInteractionController(plugin, stateManager, openRenderer, this);
        this.upgradeInteractionController = new GemUpgradeGuiInteractionController(plugin, stateManager, upgradeRenderer, this);
    }

    public boolean open(Player player) {
        return open(player, plugin.appConfig().gui().defaultMode());
    }

    public boolean open(Player player, GemGuiMode mode) {
        return switch (normalizeMode(mode)) {
            case INLAY, EXTRACT -> openEmptyGem(player, mode);
            case OPEN_SOCKET -> openSocketFromHand(player);
            case UPGRADE -> openUpgradeFromHand(player);
        };
    }

    public boolean open(Player player, GemGuiMode mode, ItemStack initialItem) {
        return switch (normalizeMode(mode)) {
            case INLAY, EXTRACT -> openEmptyGem(player, mode);
            case OPEN_SOCKET -> openSocket(player, initialItem);
            case UPGRADE -> openUpgrade(player, initialItem);
        };
    }

    public boolean openSocket(Player player) {
        return open(player, GemGuiMode.OPEN_SOCKET);
    }

    public boolean openSocket(Player player, ItemStack initialTarget) {
        return openSocket(player, initialTarget, null);
    }

    public boolean openUpgrade(Player player) {
        return open(player, GemGuiMode.UPGRADE);
    }

    public boolean openUpgrade(Player player, ItemStack initialGem) {
        return openUpgrade(player, initialGem, null);
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

    boolean switchUpgradeTemplate(GemUpgradeGuiSession state) {
        if (state == null) {
            return false;
        }
        state.setTemplateSwitching(true);
        boolean opened = openUpgrade(state.player(), state.mutableTargetGem(), state);
        if (!opened) {
            state.setTemplateSwitching(false);
        }
        return opened;
    }

    private boolean openEmptyGem(Player player, GemGuiMode mode) {
        return openGem(player, mode, null);
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

    private boolean openSocketFromHand(Player player) {
        ItemStack initialTarget = player == null ? null : cloneNonAir(player.getInventory().getItemInMainHand());
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(initialTarget);
        ItemStack loadedTarget = itemDefinition == null ? null : initialTarget;
        boolean opened = openSocket(player, loadedTarget);
        if (opened && loadedTarget != null) {
            player.getInventory().setItemInMainHand(null);
        }
        return opened;
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

    private boolean openUpgradeFromHand(Player player) {
        return openUpgrade(player, null);
    }

    private boolean openUpgrade(Player player, ItemStack initialGem, GemUpgradeGuiSession existingState) {
        if (player == null) {
            return false;
        }
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(initialGem);
        GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        GuiTemplate template = GemGuiTemplates.resolveUpgradeTemplate(plugin.guiTemplateLoader(), definition);
        if (template == null) {
            plugin.messageService().send(player, "gui.upgrade_open_failed");
            return false;
        }
        GemUpgradeGuiSession state = existingState == null ? new GemUpgradeGuiSession(player) : existingState;
        state.setTargetGem(initialGem);
        GuiSession session = openGui(player, template, (guiSession, slot) -> upgradeRenderer.renderSlot(state, slot),
                upgradeInteractionController.createSessionHandler(state));
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
            emaki.jiuwu.craft.corelib.gui.GuiSessionHandler handler) {
        return guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of(),
                (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount),
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

    public GemUpgradeGuiSession getUpgradeSession(Player player) {
        return stateManager.getUpgrade(player);
    }

    public void clearAllSessions() {
        stateManager.clear();
    }

    private GemGuiMode normalizeMode(GemGuiMode mode) {
        return mode == null ? GemGuiMode.INLAY : mode;
    }

    private GemGuiMode normalizeGemMode(GemGuiMode mode) {
        return mode == GemGuiMode.EXTRACT ? GemGuiMode.EXTRACT : GemGuiMode.INLAY;
    }

    private ItemStack cloneNonAir(ItemStack itemStack) {
        return GemGuiSession.cloneNonAir(itemStack);
    }
}
