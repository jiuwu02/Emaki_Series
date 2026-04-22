package emaki.jiuwu.craft.forge.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class EditorGuiService {

    private static final long INPUT_TIMEOUT = 120_000L;
    private static final String GUI_ID = "editor_gui";

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final EditorStateManager stateManager;
    private final EditorReloadService reloadService;
    private final EditorPersistenceService persistenceService;
    private final EditorInputService inputService;
    private final ConfiguredGuiSupport guiSupport;
    private final EditorFieldResolver fieldResolver;
    private final EditorGuiInteractionHandler interactionHandler;
    private final EditorGuiRenderer renderer;
    private final GuiTemplate fallbackTemplate;

    public EditorGuiService(EmakiForgePlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = new EditorStateManager();
        this.reloadService = new EditorReloadService(plugin, stateManager);
        this.inputService = new EditorInputService(plugin, this, stateManager);
        this.persistenceService = new EditorPersistenceService(plugin, stateManager, reloadService);
        this.guiSupport = new ConfiguredGuiSupport(plugin);
        this.fieldResolver = new EditorFieldResolver(plugin);
        this.interactionHandler = new EditorGuiInteractionHandler(
                plugin,
                this,
                stateManager,
                persistenceService,
                fieldResolver,
                INPUT_TIMEOUT
        );
        this.renderer = new EditorGuiRenderer(this, guiSupport, fieldResolver, interactionHandler, GUI_ID);
        this.fallbackTemplate = renderer.createTemplate();
    }

    public EditorInputService inputService() {
        return inputService;
    }

    public boolean openIndex(Player player) {
        if (!prepareExternalOpen(player)) {
            return false;
        }
        return openSession(new EditorSession(player, null, null, null, EditorSession.Mode.INDEX));
    }

    public boolean openExisting(Player player, EditableResourceType type, String resourceId) {
        if (player == null || type == null || Texts.isBlank(resourceId) || !prepareExternalOpen(player)) {
            return false;
        }
        String id = resourceId.trim();
        if (!fieldResolver.exists(type, id)) {
            plugin.messageService().sendRaw(player, "<red>未找到资源: " + id + "</red>");
            return false;
        }
        if (stateManager.isLockedByOther(type, id, player.getUniqueId())) {
            plugin.messageService().sendRaw(player, "<red>该资源正在被其他会话编辑。</red>");
            return false;
        }
        EditorSession session = persistenceService.openExisting(player, type, id);
        if (session == null) {
            plugin.messageService().sendRaw(player, "<red>打开资源失败。</red>");
            return false;
        }
        return openSession(session);
    }

    public boolean createNew(Player player, EditableResourceType type, String requestedId) {
        if (player == null || type == null || !prepareExternalOpen(player)) {
            return false;
        }
        String id = requestedId == null ? null : requestedId.trim();
        if (Texts.isNotBlank(id) && fieldResolver.exists(type, id)) {
            plugin.messageService().sendRaw(player, "<red>该 ID 已存在。</red>");
            return false;
        }
        EditorSession session = persistenceService.createNew(player, type, id);
        if (session == null) {
            plugin.messageService().sendRaw(player, "<red>创建资源草稿失败。</red>");
            return false;
        }
        return openSession(session);
    }

    public boolean deleteDirect(Player player, EditableResourceType type, String resourceId) {
        if (player == null || type == null || Texts.isBlank(resourceId)) {
            return false;
        }
        var result = persistenceService.deleteDirect(player, type, resourceId.trim());
        plugin.messageService().sendRaw(player, result.message());
        return result.success();
    }

    public boolean hasSession(Player player) {
        return stateManager.get(player) != null;
    }

    public void clearAllSessions() {
        reloadService.closeAllEditorSessions();
    }

    public void abandonSession(Player player) {
        if (player == null) {
            return;
        }
        EditorSession session = stateManager.get(player);
        if (session == null) {
            return;
        }
        session.clearPendingInput();
        session.setClosingByService(true);
        stateManager.remove(player);
    }

    public void resume(Player player) {
        EditorSession session = stateManager.get(player);
        if (session != null) {
            openSession(session);
        }
    }

    private boolean prepareExternalOpen(Player player) {
        if (player == null) {
            return false;
        }
        EditorSession existing = stateManager.get(player);
        if (existing == null) {
            return true;
        }
        if (existing.pendingInput() != null) {
            plugin.messageService().sendRaw(player, "<yellow>当前有待完成输入，输入 <white>cancel</white> 可取消。</yellow>");
            return false;
        }
        if (existing.dirty() && existing.editingDocument()) {
            existing.setMode(EditorSession.Mode.CONFIRM_CLOSE);
            existing.setPage(0);
            openSession(existing);
            return false;
        }
        existing.setClosingByService(true);
        player.closeInventory();
        stateManager.remove(player);
        return true;
    }

    boolean openSession(EditorSession session) {
        Player player = player(session);
        if (player == null || !player.isOnline()) {
            return false;
        }
        session.clickActions().clear();
        session.setClosingByService(false);
        if (session.guiSession() != null) {
            session.setSuspendCloseHandling(true);
        }
        GuiSession guiSession = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                guiSupport.template(GUI_ID, fallbackTemplate),
                Map.of("title", renderer.title(session)),
                plugin.itemIdentifierService()::createItem,
                (gui, slot) -> renderer.render(session, slot.inventorySlot()),
                new Handler(session)
        ));
        if (guiSession == null) {
            return false;
        }
        session.setGuiSession(guiSession);
        stateManager.put(session);
        return true;
    }

    Player player(EditorSession session) {
        return session == null ? null : plugin.getServer().getPlayer(session.playerId());
    }

    private final class Handler implements GuiSessionHandler {

        private final EditorSession session;

        private Handler(EditorSession session) {
            this.session = session;
        }

        @Override
        public void onSlotClick(GuiSession guiSession, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            interactionHandler.handleSlotClick(session, slot, event);
        }

        @Override
        public void onClose(GuiSession guiSession, InventoryCloseEvent event) {
            interactionHandler.handleClose(session);
        }
    }
}
