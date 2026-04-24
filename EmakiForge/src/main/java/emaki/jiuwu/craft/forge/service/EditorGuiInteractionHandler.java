package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

final class EditorGuiInteractionHandler {

    private final EmakiForgePlugin plugin;
    private final EditorGuiService guiService;
    private final EditorStateManager stateManager;
    private final EditorPersistenceService persistenceService;
    private final EditorFieldResolver fieldResolver;
    private final long inputTimeout;

    EditorGuiInteractionHandler(EmakiForgePlugin plugin,
            EditorGuiService guiService,
            EditorStateManager stateManager,
            EditorPersistenceService persistenceService,
            EditorFieldResolver fieldResolver,
            long inputTimeout) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = stateManager;
        this.persistenceService = persistenceService;
        this.fieldResolver = fieldResolver;
        this.inputTimeout = inputTimeout;
    }

    void handleSlotClick(EditorSession session, GuiTemplate.ResolvedSlot slot, InventoryClickEvent event) {
        if (slot == null) {
            return;
        }
        EditorSession.ClickAction action = session.clickActions().get(slot.inventorySlot());
        if (action != null) {
            action.execute(session, event);
        }
    }

    void handleClose(EditorSession session) {
        if (session.closingByService()) {
            stateManager.remove(session.playerId());
            return;
        }
        if (session.suspendCloseHandling()) {
            session.setSuspendCloseHandling(false);
            return;
        }
        if (session.pendingInput() != null) {
            return;
        }
        if (session.dirty() && session.editingDocument()) {
            session.setMode(EditorSession.Mode.CONFIRM_CLOSE);
            plugin.getServer().getScheduler().runTask(plugin, () -> guiService.openSession(session));
            return;
        }
        stateManager.remove(session.playerId());
    }

    void showType(EditorSession session, EditableResourceType type) {
        session.setMode(EditorSession.Mode.LIST);
        session.setResourceType(type);
        session.setPage(0);
        guiService.openSession(session);
    }

    void handleEntry(EditorSession session, EditorFieldResolver.Entry entry, InventoryClickEvent event) {
        Object value = entry.value();
        if (value instanceof java.util.Map<?, ?> || value instanceof List<?>) {
            if (event.isShiftClick() && event.isRightClick()) {
                removeEntry(session, entry);
                return;
            }
            session.setCurrentPath(entry.path());
            session.setPage(0);
            session.setMode(EditorSchemaSupport.isSlotListPath(session.resourceType(), entry.path())
                    ? EditorSession.Mode.SLOT_GRID
                    : EditorSession.Mode.DOCUMENT);
            guiService.openSession(session);
            return;
        }
        if (event.isRightClick() && EditorSchemaSupport.isItemSourcePath(entry.path()) && !event.isShiftClick()) {
            applyHeldSource(session, entry.path(), entry.token());
            return;
        }
        if (event.isRightClick()) {
            removeEntry(session, entry);
            return;
        }
        promptScalar(session, entry.path(), value);
    }

    void addNode(EditorSession session) {
        Object node = session.currentNode();
        if (node instanceof java.util.Map<?, ?>) {
            startInput(session, "请输入新字段，支持 key=value、key={}、key=[]，输入 cancel 取消。", input -> {
                EditorFieldResolver.FieldInput fieldInput = fieldResolver.parseFieldInput(input);
                if (fieldInput == null) {
                    plugin.messageService().sendRaw(player(session), "<red>输入格式无效。</red>");
                    guiService.openSession(session);
                    return;
                }
                session.putMapValue(session.currentPath(), fieldInput.key(), fieldInput.value());
                guiService.openSession(session);
            });
            return;
        }
        if (node instanceof List<?>) {
            Object templateValue = EditorSchemaSupport.defaultListEntry(session.resourceType(), session.currentPath());
            if (templateValue instanceof java.util.Map<?, ?> || templateValue instanceof List<?>) {
                session.addListValue(session.currentPath(), templateValue);
                Object listNode = session.currentNode();
                int newIndex = listNode instanceof List<?> list ? list.size() - 1 : -1;
                if (newIndex >= 0) {
                    session.setCurrentPath(fieldResolver.append(session.currentPath(), "#" + newIndex));
                    session.setMode(EditorSession.Mode.DOCUMENT);
                    session.setPage(0);
                }
                guiService.openSession(session);
                return;
            }
            startInput(session, "请输入要追加的列表值，输入 cancel 取消。", input -> {
                session.addListValue(session.currentPath(), input);
                guiService.openSession(session);
            });
        }
    }

    void promptScalar(EditorSession session, List<String> path, Object currentValue) {
        startInput(session, "请输入新值。当前值: " + fieldResolver.brief(Texts.toStringSafe(currentValue)) + "，输入 cancel 取消。", input -> {
            session.setNode(path, input);
            guiService.openSession(session);
        });
    }

    void save(EditorSession session) {
        handlePersistenceResult(session, persistenceService.save(session));
    }

    void delete(EditorSession session) {
        handlePersistenceResult(session, persistenceService.delete(session));
    }

    private void handlePersistenceResult(EditorSession session, EditorPersistenceService.OperationResult result) {
        Player player = player(session);
        if (player != null) {
            plugin.messageService().sendRaw(player, result.message());
        }
        if (!result.success()) {
            guiService.openSession(session);
        }
    }

    void applyHeldSource(EditorSession session, List<String> path, String fieldHint) {
        Player player = player(session);
        if (player == null) {
            return;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(player.getInventory().getItemInMainHand());
        if (source == null) {
            plugin.messageService().sendRaw(player, "<red>主手物品无法识别为 source。</red>");
            guiService.openSession(session);
            return;
        }
        String shorthand = ItemSourceUtil.toShorthand(source);
        if (Texts.isBlank(shorthand)) {
            shorthand = source.getType().name().toLowerCase(Locale.ROOT) + ":" + source.getIdentifier();
        }
        if (path != null
                && !path.isEmpty()
                && ("item".equals(path.get(path.size() - 1)) || "output_item".equals(path.get(path.size() - 1)))) {
            session.setNode(path, shorthand);
        } else if (path != null) {
            session.putMapValue(path, fieldHint, shorthand);
        }
        plugin.messageService().sendRaw(player, "<green>已写入 source: " + shorthand + "</green>");
        guiService.openSession(session);
    }

    void goBack(EditorSession session) {
        if (!session.currentPath().isEmpty()) {
            List<String> path = new ArrayList<>(session.currentPath());
            path.remove(path.size() - 1);
            session.setCurrentPath(path);
            session.setMode(EditorSession.Mode.DOCUMENT);
            session.setPage(0);
            guiService.openSession(session);
            return;
        }
        if (session.dirty()) {
            session.setMode(EditorSession.Mode.CONFIRM_CLOSE);
            guiService.openSession(session);
            return;
        }
        releaseDocument(session);
        session.setMode(EditorSession.Mode.LIST);
        session.setPage(0);
        guiService.openSession(session);
    }

    void requestClose(EditorSession session) {
        if (session.dirty()) {
            session.setMode(EditorSession.Mode.CONFIRM_CLOSE);
            guiService.openSession(session);
            return;
        }
        forceClose(session);
    }

    void forceClose(EditorSession session) {
        releaseDocument(session);
        closeSession(session);
    }

    void closeSession(EditorSession session) {
        session.setClosingByService(true);
        Player player = player(session);
        if (player != null && player.isOnline()) {
            player.closeInventory();
        }
        stateManager.remove(session.playerId());
    }

    private void removeEntry(EditorSession session, EditorFieldResolver.Entry entry) {
        List<String> parent = new ArrayList<>(entry.path());
        if (parent.isEmpty()) {
            return;
        }
        String token = parent.remove(parent.size() - 1);
        if (token.startsWith("#")) {
            session.removeListValue(parent, fieldResolver.parseIndex(token));
        } else {
            session.removeMapValue(parent, token);
        }
        guiService.openSession(session);
    }

    private void releaseDocument(EditorSession session) {
        if (session.resourceType() != null && Texts.isNotBlank(session.originalId())) {
            stateManager.releaseLock(session.resourceType(), session.originalId(), session.playerId());
        }
        session.setOriginalId(null);
        session.setRootData(null);
        session.setCurrentPath(List.of());
        session.setDirty(false);
    }

    private void startInput(EditorSession session, String prompt, Consumer<String> submitHandler) {
        Player player = player(session);
        if (player == null) {
            return;
        }
        session.setPendingInput(new EditorSession.PendingInput(prompt, System.currentTimeMillis() + inputTimeout, submitHandler));
        session.setSuspendCloseHandling(true);
        player.closeInventory();
        plugin.messageService().sendRaw(player, "<gold>[锻造编辑器输入]</gold> " + prompt);
        plugin.messageService().sendRaw(
                player,
                "<gray>请使用 <white>/emakiforge input <内容></white> 提交，或使用 <white>/emakiforge input cancel</white> 取消。</gray>"
        );
    }

    private Player player(EditorSession session) {
        return guiService.player(session);
    }
}
