package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class EditorGuiService {

    private record Entry(String label, String token, Object value, List<String> path) {}

    private record FieldInput(String key, Object value) {}

    private static final List<Integer> CONTENT = slots(0, 45);
    private static final List<Integer> ALL = slots(0, 54);
    private static final long INPUT_TIMEOUT = 120_000L;
    private static final String GUI_ID = "editor_gui";

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final EditorStateManager stateManager;
    private final EditorReloadService reloadService;
    private final EditorPersistenceService persistenceService;
    private final EditorInputService inputService;
    private final ConfiguredGuiSupport guiSupport;
    private final GuiTemplate fallbackTemplate;

    public EditorGuiService(EmakiForgePlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.stateManager = new EditorStateManager();
        this.reloadService = new EditorReloadService(plugin, stateManager);
        this.persistenceService = new EditorPersistenceService(plugin, stateManager, reloadService);
        this.inputService = new EditorInputService(plugin, this, stateManager);
        this.guiSupport = new ConfiguredGuiSupport(plugin);
        this.fallbackTemplate = createTemplate();
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
        if (!exists(type, id)) {
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
        if (Texts.isNotBlank(id) && exists(type, id)) {
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

    private boolean openSession(EditorSession session) {
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
                Map.of("title", title(session)),
                plugin.itemIdentifierService()::createItem,
                (gui, slot) -> render(session, slot.inventorySlot()),
                new Handler(session)
        ));
        if (guiSession == null) {
            return false;
        }
        session.setGuiSession(guiSession);
        stateManager.put(session);
        return true;
    }

    private ItemStack render(EditorSession session, int slot) {
        return switch (session.mode()) {
            case INDEX -> renderIndex(session, slot);
            case LIST -> renderList(session, slot);
            case DOCUMENT -> renderDocument(session, slot);
            case SLOT_GRID -> renderGrid(session, slot);
            case CONFIRM_CLOSE -> renderConfirm(session, slot);
        };
    }

    private Player player(EditorSession session) {
        return session == null ? null : plugin.getServer().getPlayer(session.playerId());
    }

    private static List<Integer> slots(int from, int to) {
        List<Integer> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            out.add(i);
        }
        return List.copyOf(out);
    }

    private GuiTemplate createTemplate() {
        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        slots.put("editor", new GuiSlot("editor", ALL, "editor", "GRAY_STAINED_GLASS_PANE", ItemComponentParser.empty(), Map.of()));
        return new GuiTemplate(GUI_ID, "<gold>{title}</gold>", 6, slots);
    }

    private ItemStack renderIndex(EditorSession session, int slot) {
        if (buttonSlot(session, "blueprint", slot)) {
            return clickable(session, slot, typeItem(EditableResourceType.BLUEPRINT),
                    (editorSession, event) -> showType(editorSession, EditableResourceType.BLUEPRINT));
        }
        if (buttonSlot(session, "material", slot)) {
            return clickable(session, slot, typeItem(EditableResourceType.MATERIAL),
                    (editorSession, event) -> showType(editorSession, EditableResourceType.MATERIAL));
        }
        if (buttonSlot(session, "recipe", slot)) {
            return clickable(session, slot, typeItem(EditableResourceType.RECIPE),
                    (editorSession, event) -> showType(editorSession, EditableResourceType.RECIPE));
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", Map.of(), "BARRIER", new ItemComponentParser.ItemComponents(
                    "<red>关闭编辑器</red>", true, List.of("<gray>关闭当前界面。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> closeSession(editorSession));
        }
        return null;
    }

    private ItemStack renderList(EditorSession session, int slot) {
        EditableResourceType type = session.resourceType();
        if (type == null) {
            session.setMode(EditorSession.Mode.INDEX);
            return renderIndex(session, slot);
        }
        List<String> ids = ids(type);
        List<Integer> contentSlots = contentSlots(session);
        int pageSize = Math.max(1, contentSlots.size());
        int pages = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
        session.setPage(Math.min(session.page(), pages - 1));
        int idx = contentSlots.indexOf(slot);
        if (idx >= 0) {
            int actual = session.page() * pageSize + idx;
            if (actual >= ids.size()) {
                return null;
            }
            String id = ids.get(actual);
            return clickable(session, slot, resourceItem(type, id),
                    (editorSession, event) -> openExisting((Player) event.getWhoClicked(), type, id));
        }
        Map<String, Object> replacements = Map.of(
                "page", session.page() + 1,
                "pages", pages,
                "type_name", type.displayName(),
                "count", ids.size()
        );
        if (buttonSlot(session, "back", slot)) {
            return clickable(session, slot, buttonItem(session, "back", replacements, "COMPASS", new ItemComponentParser.ItemComponents(
                    "<yellow>返回分类</yellow>", true, List.of("<gray>回到资源类型选择。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.INDEX);
                        editorSession.setPage(0);
                        editorSession.setResourceType(null);
                        openSession(editorSession);
                    });
        }
        if (buttonSlot(session, "prev_page", slot)) {
            return clickable(session, slot, buttonItem(session, "prev_page", replacements, "ARROW", new ItemComponentParser.ItemComponents(
                    "<yellow>上一页</yellow>", true, List.of("<gray>{page}/{pages}</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        if (editorSession.page() > 0) {
                            editorSession.setPage(editorSession.page() - 1);
                            openSession(editorSession);
                        }
                    });
        }
        if (buttonSlot(session, "create", slot)) {
            return clickable(session, slot, buttonItem(session, "create", replacements, "LIME_DYE", new ItemComponentParser.ItemComponents(
                    "<green>创建新{type_name}</green>", true, List.of("<gray>创建新的资源草稿。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> createNew((Player) event.getWhoClicked(), type, null));
        }
        if (buttonSlot(session, "summary", slot)) {
            return buttonItem(session, "summary", replacements, "BOOK", new ItemComponentParser.ItemComponents(
                    "<gold>{type_name}列表</gold>", true, List.of("<gray>总数: {count}</gray>"), null, null, Map.of(), List.of()));
        }
        if (buttonSlot(session, "next_page", slot)) {
            return clickable(session, slot, buttonItem(session, "next_page", replacements, "ARROW", new ItemComponentParser.ItemComponents(
                    "<yellow>下一页</yellow>", true, List.of("<gray>{page}/{pages}</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        if (editorSession.page() + 1 < pages) {
                            editorSession.setPage(editorSession.page() + 1);
                            openSession(editorSession);
                        }
                    });
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", replacements, "BARRIER", new ItemComponentParser.ItemComponents(
                    "<red>关闭编辑器</red>", true, List.of("<gray>关闭当前界面。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> closeSession(editorSession));
        }
        return null;
    }

    private ItemStack renderDocument(EditorSession session, int slot) {
        Object node = session.currentNode() == null ? session.rootData() : session.currentNode();
        List<Entry> entries = entries(session, node);
        List<Integer> contentSlots = contentSlots(session);
        int pageSize = Math.max(1, contentSlots.size());
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        session.setPage(Math.min(session.page(), pages - 1));
        int idx = contentSlots.indexOf(slot);
        if (idx >= 0) {
            int actual = session.page() * pageSize + idx;
            if (actual >= entries.size()) {
                return null;
            }
            Entry entry = entries.get(actual);
            return clickable(session, slot, nodeItem(session, entry),
                    (editorSession, event) -> handleEntry(editorSession, entry, event));
        }
        String sourceKey = sourceField(node);
        boolean slotList = EditorSchemaSupport.isSlotListPath(session.resourceType(), session.currentPath());
        Map<String, Object> replacements = Map.of(
                "page", session.page() + 1,
                "pages", pages,
                "path", path(session.currentPath()),
                "node_type", nodeType(node),
                "source_key", sourceKey == null ? "" : sourceKey
        );
        if (buttonSlot(session, "back", slot)) {
            return clickable(session, slot, buttonItem(session, "back", replacements, "COMPASS", new ItemComponentParser.ItemComponents(
                    "<yellow>返回上级</yellow>", true, List.of("<gray>{path}</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> goBack(editorSession));
        }
        if (buttonSlot(session, "prev_page", slot)) {
            return clickable(session, slot, buttonItem(session, "prev_page", replacements, "ARROW", new ItemComponentParser.ItemComponents(
                    "<yellow>上一页</yellow>", true, List.of("<gray>{page}/{pages}</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        if (editorSession.page() > 0) {
                            editorSession.setPage(editorSession.page() - 1);
                            openSession(editorSession);
                        }
                    });
        }
        if (buttonSlot(session, "add", slot)) {
            return clickable(session, slot, buttonItem(session, "add", replacements, "LIME_DYE", new ItemComponentParser.ItemComponents(
                    "<green>新增条目</green>", true, List.of("<gray>Map 新增字段，List 追加元素。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> addNode(editorSession));
        }
        if (buttonSlot(session, "save", slot)) {
            return clickable(session, slot, buttonItem(session, "save", replacements, "EMERALD", new ItemComponentParser.ItemComponents(
                    "<green>保存并热重载</green>", true, List.of("<gray>保存当前资源。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> save(editorSession));
        }
        if (buttonSlot(session, "context", slot)) {
            if (sourceKey == null) {
                return buttonItem(session, "context", replacements, "WRITABLE_BOOK", new ItemComponentParser.ItemComponents(
                        "<gold>当前节点</gold>", true, List.of("<gray>{path}</gray>", "<gray>{node_type}</gray>"), null, null, Map.of(), List.of()));
            }
            return clickable(session, slot, buttonItem(session, "context_source", replacements, "ITEM_FRAME", new ItemComponentParser.ItemComponents(
                    "<gold>读取主手物品</gold>", true, List.of("<gray>写入 {source_key} 字段。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> applyHeldSource(editorSession, editorSession.currentPath(), sourceKey));
        }
        if (buttonSlot(session, "grid", slot)) {
            if (!slotList) {
                return null;
            }
            return clickable(session, slot, buttonItem(session, "grid", replacements, "CHEST", new ItemComponentParser.ItemComponents(
                    "<aqua>打开槽位网格</aqua>", true, List.of("<gray>切换当前配方槽位。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.SLOT_GRID);
                        editorSession.setPage(0);
                        openSession(editorSession);
                    });
        }
        if (buttonSlot(session, "delete", slot)) {
            return clickable(session, slot, buttonItem(session, "delete", replacements, "TNT", new ItemComponentParser.ItemComponents(
                    "<red>删除当前资源</red>", true, List.of("<gray>会先备份再删除。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> delete(editorSession));
        }
        if (buttonSlot(session, "next_page", slot)) {
            return clickable(session, slot, buttonItem(session, "next_page", replacements, "ARROW", new ItemComponentParser.ItemComponents(
                    "<yellow>下一页</yellow>", true, List.of("<gray>{page}/{pages}</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        if (editorSession.page() + 1 < pages) {
                            editorSession.setPage(editorSession.page() + 1);
                            openSession(editorSession);
                        }
                    });
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", replacements, "BARRIER", new ItemComponentParser.ItemComponents(
                    "<red>关闭编辑器</red>", true, List.of("<gray>未保存更改会先确认。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> requestClose(editorSession));
        }
        return null;
    }

    private ItemStack renderGrid(EditorSession session, int slot) {
        List<Integer> values = toIntList(session.currentNode());
        List<Integer> contentSlots = contentSlots(session);
        int pageSize = Math.max(1, contentSlots.size());
        int pages = Math.max(1, (54 + pageSize - 1) / pageSize);
        int contentIndex = contentSlots.indexOf(slot);
        if (contentIndex >= 0) {
            int target = session.page() * pageSize + contentIndex;
            if (target >= 54) {
                return null;
            }
            boolean on = values.contains(target);
            Map<String, Object> replacements = Map.of("slot", target, "state", on ? "enabled" : "disabled");
            return clickable(session, slot, gridItem(on, replacements),
                    (editorSession, event) -> {
                        editorSession.toggleSlotValue(editorSession.currentPath(), target);
                        openSession(editorSession);
                    });
        }
        Map<String, Object> replacements = Map.of(
                "page", session.page() + 1,
                "pages", pages,
                "count", values.size(),
                "path", path(session.currentPath())
        );
        if (buttonSlot(session, "back_document", slot)) {
            return clickable(session, slot, buttonItem(session, "back_document", replacements, "COMPASS", new ItemComponentParser.ItemComponents(
                    "<yellow>返回文档</yellow>", true, List.of("<gray>回到文档树。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.DOCUMENT);
                        editorSession.setPage(0);
                        openSession(editorSession);
                    });
        }
        if (buttonSlot(session, "prev_page", slot)) {
            return clickable(session, slot, buttonItem(session, "prev_page", replacements, "ARROW", new ItemComponentParser.ItemComponents(
                    "<yellow>上一页</yellow>", true, List.of("<gray>{page}/{pages}</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        if (editorSession.page() > 0) {
                            editorSession.setPage(editorSession.page() - 1);
                            openSession(editorSession);
                        }
                    });
        }
        if (buttonSlot(session, "save", slot)) {
            return clickable(session, slot, buttonItem(session, "save", replacements, "EMERALD", new ItemComponentParser.ItemComponents(
                    "<green>保存并热重载</green>", true, List.of("<gray>直接保存当前配方。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> save(editorSession));
        }
        if (buttonSlot(session, "summary", slot)) {
            return buttonItem(session, "summary", replacements, "BOOK", new ItemComponentParser.ItemComponents(
                    "<gold>槽位数量: {count}</gold>", true, List.of("<gray>{path}</gray>"), null, null, Map.of(), List.of()));
        }
        if (buttonSlot(session, "next_page", slot)) {
            return clickable(session, slot, buttonItem(session, "next_page", replacements, "ARROW", new ItemComponentParser.ItemComponents(
                    "<yellow>下一页</yellow>", true, List.of("<gray>{page}/{pages}</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        if (editorSession.page() + 1 < pages) {
                            editorSession.setPage(editorSession.page() + 1);
                            openSession(editorSession);
                        }
                    });
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", replacements, "BARRIER", new ItemComponentParser.ItemComponents(
                    "<red>关闭编辑器</red>", true, List.of("<gray>未保存更改会先确认。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> requestClose(editorSession));
        }
        return null;
    }

    private ItemStack renderConfirm(EditorSession session, int slot) {
        if (buttonSlot(session, "save_exit", slot)) {
            return clickable(session, slot, buttonItem(session, "save_exit", Map.of(), "EMERALD", new ItemComponentParser.ItemComponents(
                    "<green>保存并退出</green>", true, List.of("<gray>保存资源后关闭。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> save(editorSession));
        }
        if (buttonSlot(session, "discard", slot)) {
            return clickable(session, slot, buttonItem(session, "discard", Map.of(), "LAVA_BUCKET", new ItemComponentParser.ItemComponents(
                    "<red>放弃修改</red>", true, List.of("<gray>丢弃当前更改。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> forceClose(editorSession));
        }
        if (buttonSlot(session, "return", slot)) {
            return clickable(session, slot, buttonItem(session, "return", Map.of(), "COMPASS", new ItemComponentParser.ItemComponents(
                    "<yellow>返回编辑</yellow>", true, List.of("<gray>回到当前文档。</gray>"), null, null, Map.of(), List.of())),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.DOCUMENT);
                        openSession(editorSession);
                    });
        }
        return null;
    }

    private void showType(EditorSession session, EditableResourceType type) {
        session.setMode(EditorSession.Mode.LIST);
        session.setResourceType(type);
        session.setPage(0);
        openSession(session);
    }

    private List<String> ids(EditableResourceType type) {
        List<String> out = switch (type) {
            case BLUEPRINT -> new ArrayList<>(plugin.blueprintLoader().all().keySet());
            case MATERIAL -> new ArrayList<>(plugin.materialLoader().all().keySet());
            case RECIPE -> new ArrayList<>(plugin.recipeLoader().all().keySet());
        };
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    private boolean exists(EditableResourceType type, String id) {
        return switch (type) {
            case BLUEPRINT -> plugin.blueprintLoader().get(id) != null;
            case MATERIAL -> plugin.materialLoader().get(id) != null;
            case RECIPE -> plugin.recipeLoader().get(id) != null;
        };
    }

    private List<Entry> entries(EditorSession session, Object node) {
        List<Entry> out = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey());
                out.add(new Entry(key, key, entry.getValue(), append(session.currentPath(), key)));
            }
            out.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                out.add(new Entry("[" + i + "]", "#" + i, list.get(i), append(session.currentPath(), "#" + i)));
            }
        }
        return out;
    }

    private void handleEntry(EditorSession session, Entry entry, InventoryClickEvent event) {
        Object value = entry.value();
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            if (event.isShiftClick() && event.isRightClick()) {
                removeEntry(session, entry);
                return;
            }
            session.setCurrentPath(entry.path());
            session.setPage(0);
            session.setMode(EditorSchemaSupport.isSlotListPath(session.resourceType(), entry.path())
                    ? EditorSession.Mode.SLOT_GRID
                    : EditorSession.Mode.DOCUMENT);
            openSession(session);
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

    private void addNode(EditorSession session) {
        Object node = session.currentNode();
        if (node instanceof Map<?, ?>) {
            startInput(session, "请输入新字段，支持 key=value、key={}、key=[]，输入 cancel 取消。", input -> {
                FieldInput fieldInput = parseFieldInput(input);
                if (fieldInput == null) {
                    plugin.messageService().sendRaw(player(session), "<red>输入格式无效。</red>");
                    openSession(session);
                    return;
                }
                session.putMapValue(session.currentPath(), fieldInput.key(), fieldInput.value());
                openSession(session);
            });
            return;
        }
        if (node instanceof List<?>) {
            Object templateValue = EditorSchemaSupport.defaultListEntry(session.resourceType(), session.currentPath());
            if (templateValue instanceof Map<?, ?> || templateValue instanceof List<?>) {
                session.addListValue(session.currentPath(), templateValue);
                Object listNode = session.currentNode();
                int newIndex = listNode instanceof List<?> list ? list.size() - 1 : -1;
                if (newIndex >= 0) {
                    session.setCurrentPath(append(session.currentPath(), "#" + newIndex));
                    session.setMode(EditorSession.Mode.DOCUMENT);
                    session.setPage(0);
                }
                openSession(session);
                return;
            }
            startInput(session, "请输入要追加的列表值，输入 cancel 取消。", input -> {
                session.addListValue(session.currentPath(), input);
                openSession(session);
            });
        }
    }

    private void promptScalar(EditorSession session, List<String> path, Object currentValue) {
        startInput(session, "请输入新值。当前值: " + brief(Texts.toStringSafe(currentValue)) + "，输入 cancel 取消。", input -> {
            session.setNode(path, input);
            openSession(session);
        });
    }

    private void save(EditorSession session) {
        var result = persistenceService.save(session);
        Player player = player(session);
        if (player != null) {
            plugin.messageService().sendRaw(player, result.message());
        }
        if (!result.success()) {
            openSession(session);
        }
    }

    private void delete(EditorSession session) {
        var result = persistenceService.delete(session);
        Player player = player(session);
        if (player != null) {
            plugin.messageService().sendRaw(player, result.message());
        }
        if (!result.success()) {
            openSession(session);
        }
    }

    private void applyHeldSource(EditorSession session, List<String> path, String fieldHint) {
        Player player = player(session);
        if (player == null) {
            return;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(player.getInventory().getItemInMainHand());
        if (source == null) {
            plugin.messageService().sendRaw(player, "<red>主手物品无法识别为 source。</red>");
            openSession(session);
            return;
        }
        String shorthand = ItemSourceUtil.toShorthand(source);
        if (Texts.isBlank(shorthand)) {
            shorthand = source.getType().name().toLowerCase(Locale.ROOT) + ":" + source.getIdentifier();
        }
        if (path != null && !path.isEmpty() && ("item".equals(path.get(path.size() - 1)) || "output_item".equals(path.get(path.size() - 1)))) {
            session.setNode(path, shorthand);
        } else if (path != null) {
            session.putMapValue(path, fieldHint, shorthand);
        }
        plugin.messageService().sendRaw(player, "<green>已写入 source: " + shorthand + "</green>");
        openSession(session);
    }

    private void removeEntry(EditorSession session, Entry entry) {
        List<String> parent = new ArrayList<>(entry.path());
        if (parent.isEmpty()) {
            return;
        }
        String token = parent.remove(parent.size() - 1);
        if (token.startsWith("#")) {
            session.removeListValue(parent, parseIndex(token));
        } else {
            session.removeMapValue(parent, token);
        }
        openSession(session);
    }

    private void goBack(EditorSession session) {
        if (!session.currentPath().isEmpty()) {
            List<String> path = new ArrayList<>(session.currentPath());
            path.remove(path.size() - 1);
            session.setCurrentPath(path);
            session.setMode(EditorSession.Mode.DOCUMENT);
            session.setPage(0);
            openSession(session);
            return;
        }
        if (session.dirty()) {
            session.setMode(EditorSession.Mode.CONFIRM_CLOSE);
            openSession(session);
            return;
        }
        releaseDocument(session);
        session.setMode(EditorSession.Mode.LIST);
        session.setPage(0);
        openSession(session);
    }

    private void requestClose(EditorSession session) {
        if (session.dirty()) {
            session.setMode(EditorSession.Mode.CONFIRM_CLOSE);
            openSession(session);
            return;
        }
        forceClose(session);
    }

    private void forceClose(EditorSession session) {
        releaseDocument(session);
        closeSession(session);
    }

    private void closeSession(EditorSession session) {
        session.setClosingByService(true);
        Player player = player(session);
        if (player != null && player.isOnline()) {
            player.closeInventory();
        }
        stateManager.remove(session.playerId());
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
        session.setPendingInput(new EditorSession.PendingInput(prompt, System.currentTimeMillis() + INPUT_TIMEOUT, submitHandler));
        session.setSuspendCloseHandling(true);
        player.closeInventory();
        plugin.messageService().sendRaw(player, "<gold>[锻造编辑器输入]</gold> " + prompt);
    }

    private ItemStack typeItem(EditableResourceType type) {
        String key = switch (type) {
            case BLUEPRINT -> "blueprint";
            case MATERIAL -> "material";
            case RECIPE -> "recipe";
        };
        return guiSupport.build(
                GUI_ID,
                "pages.index.buttons." + key,
                Map.of("type_name", type.displayName()),
                typeIcon(type).name(),
                new ItemComponentParser.ItemComponents(
                        "<gold>{type_name}</gold>",
                        true,
                        List.of("<gray>打开{type_name}资源列表。</gray>"),
                        null,
                        null,
                        Map.of(),
                        List.of()
                )
        );
    }

    private ItemStack resourceItem(EditableResourceType type, String id) {
        return guiSupport.build(
                GUI_ID,
                "pages.list.content_item",
                Map.of(
                        "entry_id", id,
                        "entry_name", resourceName(type, id),
                        "type_name", type.displayName()
                ),
                typeIcon(type).name(),
                new ItemComponentParser.ItemComponents(
                        "<gold>{entry_id}</gold>",
                        true,
                        List.of("<gray>{entry_name}</gray>", "<gray>左键打开编辑。</gray>"),
                        null,
                        null,
                        Map.of(),
                        List.of()
                )
        );
    }

    private ItemStack nodeItem(EditorSession session, Entry entry) {
        Object value = entry.value();
        String templateKey;
        String fallbackItem;
        if (value instanceof Map<?, ?>) {
            templateKey = "map";
            fallbackItem = "CHEST";
        } else if (value instanceof List<?>) {
            templateKey = "list";
            fallbackItem = "BARREL";
        } else if ("true".equalsIgnoreCase(Texts.toStringSafe(value)) || "false".equalsIgnoreCase(Texts.toStringSafe(value))) {
            templateKey = "boolean";
            fallbackItem = "LEVER";
        } else {
            try {
                Double.parseDouble(Texts.toStringSafe(value));
                templateKey = "number";
                fallbackItem = "CLOCK";
            } catch (Exception ignored) {
                templateKey = "scalar";
                fallbackItem = "NAME_TAG";
            }
        }
        return guiSupport.build(
                GUI_ID,
                "pages.document.content_templates." + templateKey,
                Map.of(
                        "entry_label", entry.label(),
                        "node_type", nodeType(value),
                        "node_value", brief(EditorSchemaSupport.summarize(value)),
                        "is_slot_list", EditorSchemaSupport.isSlotListPath(session.resourceType(), entry.path()) ? "<aqua>支持槽位网格模式</aqua>" : "",
                        "edit_hint", value instanceof Map<?, ?> || value instanceof List<?> ? "<yellow>左键进入子节点</yellow>" : "<yellow>左键聊天输入</yellow>",
                        "delete_hint", value instanceof Map<?, ?> || value instanceof List<?> || EditorSchemaSupport.isItemSourcePath(entry.path())
                                ? "<red>Shift+右键删除</red>"
                                : "<red>右键删除</red>",
                        "secondary_hint", EditorSchemaSupport.isItemSourcePath(entry.path()) ? "<aqua>右键读取主手物品</aqua>" : ""
                ),
                fallbackItem,
                new ItemComponentParser.ItemComponents(
                        "<gold>{entry_label}</gold>",
                        true,
                        List.of(
                                "<gray>类型: {node_type}</gray>",
                                "<gray>值: {node_value}</gray>",
                                "{edit_hint}",
                                "{secondary_hint}",
                                "{delete_hint}",
                                "{is_slot_list}"
                        ),
                        null,
                        null,
                        Map.of(),
                        List.of()
                )
        );
    }

    private String resourceName(EditableResourceType type, String id) {
        return switch (type) {
            case BLUEPRINT -> {
                Blueprint value = plugin.blueprintLoader().get(id);
                yield value == null ? id : Texts.stripMiniTags(value.displayName());
            }
            case MATERIAL -> {
                ForgeMaterial value = plugin.materialLoader().get(id);
                yield value == null ? id : Texts.stripMiniTags(value.displayName());
            }
            case RECIPE -> {
                Recipe value = plugin.recipeLoader().get(id);
                yield value == null ? id : Texts.stripMiniTags(value.displayName());
            }
        };
    }

    private String title(EditorSession session) {
        String fallback = switch (session.mode()) {
            case INDEX -> "锻造编辑器";
            case LIST -> session.resourceType() == null ? "资源列表" : session.resourceType().displayName() + "列表";
            case DOCUMENT -> session.resourceType() == null ? "资源编辑" : session.resourceType().displayName() + ":" + titlePart(session.currentId());
            case SLOT_GRID -> "槽位网格";
            case CONFIRM_CLOSE -> "确认关闭";
        };
        return guiSupport.text(
                GUI_ID,
                "pages." + pageKey(session) + ".title",
                fallback,
                Map.of(
                        "type_name", session.resourceType() == null ? "" : session.resourceType().displayName(),
                        "current_id", Texts.toStringSafe(session.currentId()),
                        "path", path(session.currentPath())
                )
        );
    }

    private List<Integer> contentSlots(EditorSession session) {
        return guiSupport.slots(GUI_ID, "pages." + pageKey(session) + ".content_slots", CONTENT);
    }

    private boolean buttonSlot(EditorSession session, String buttonKey, int slot) {
        return guiSupport.slots(
                GUI_ID,
                "pages." + pageKey(session) + ".buttons." + buttonKey + ".slots",
                defaultButtonSlots(session.mode(), buttonKey)
        ).contains(slot);
    }

    private ItemStack buttonItem(EditorSession session,
            String buttonKey,
            Map<String, ?> replacements,
            String fallbackItem,
            ItemComponentParser.ItemComponents fallbackComponents) {
        return guiSupport.build(
                GUI_ID,
                "pages." + pageKey(session) + ".buttons." + buttonKey,
                replacements,
                fallbackItem,
                fallbackComponents
        );
    }

    private ItemStack gridItem(boolean enabled, Map<String, ?> replacements) {
        return guiSupport.build(
                GUI_ID,
                "pages.grid.content_templates." + (enabled ? "enabled" : "disabled"),
                replacements,
                enabled ? "LIME_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE",
                new ItemComponentParser.ItemComponents(
                        (enabled ? "<green>" : "<gray>") + "槽位 {slot}" + (enabled ? " 已启用</green>" : " 未启用</gray>"),
                        true,
                        List.of("<gray>左键切换该槽位。</gray>"),
                        null,
                        null,
                        Map.of(),
                        List.of()
                )
        );
    }

    private String pageKey(EditorSession session) {
        return switch (session.mode()) {
            case INDEX -> "index";
            case LIST -> "list";
            case DOCUMENT -> "document";
            case SLOT_GRID -> "grid";
            case CONFIRM_CLOSE -> "confirm";
        };
    }

    private List<Integer> defaultButtonSlots(EditorSession.Mode mode, String buttonKey) {
        return switch (mode) {
            case INDEX -> switch (buttonKey) {
                case "blueprint" -> List.of(11);
                case "material" -> List.of(22);
                case "recipe" -> List.of(33);
                case "close" -> List.of(49);
                default -> List.of();
            };
            case LIST -> switch (buttonKey) {
                case "back" -> List.of(45);
                case "prev_page" -> List.of(46);
                case "create" -> List.of(47);
                case "summary" -> List.of(49);
                case "next_page" -> List.of(52);
                case "close" -> List.of(53);
                default -> List.of();
            };
            case DOCUMENT -> switch (buttonKey) {
                case "back" -> List.of(45);
                case "prev_page" -> List.of(46);
                case "add" -> List.of(47);
                case "save" -> List.of(48);
                case "context" -> List.of(49);
                case "context_source" -> List.of(49);
                case "grid" -> List.of(50);
                case "delete" -> List.of(51);
                case "next_page" -> List.of(52);
                case "close" -> List.of(53);
                default -> List.of();
            };
            case SLOT_GRID -> switch (buttonKey) {
                case "back_document" -> List.of(45);
                case "prev_page" -> List.of(46);
                case "save" -> List.of(48);
                case "summary" -> List.of(49);
                case "next_page" -> List.of(52);
                case "close" -> List.of(53);
                default -> List.of();
            };
            case CONFIRM_CLOSE -> switch (buttonKey) {
                case "save_exit" -> List.of(20);
                case "discard" -> List.of(24);
                case "return" -> List.of(31);
                default -> List.of();
            };
        };
    }

    private String sourceField(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        if (map.containsKey("item")) {
            return "item";
        }
        if (map.containsKey("output_item")) {
            return "output_item";
        }
        return null;
    }

    private List<Integer> toIntList(Object node) {
        List<Integer> out = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return out;
        }
        for (Object entry : list) {
            if (entry instanceof Number number) {
                out.add(number.intValue());
                continue;
            }
            try {
                out.add(Integer.parseInt(Texts.toStringSafe(entry)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private ItemStack clickable(EditorSession session, int slot, ItemStack item, EditorSession.ClickAction action) {
        if (action != null) {
            session.clickActions().put(slot, action);
        }
        return item;
    }

    private ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, "<gray> </gray>", List.of());
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.customName(MiniMessages.parse(name));
            meta.lore(lore.stream().map(MiniMessages::parse).toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private Material typeIcon(EditableResourceType type) {
        return switch (type) {
            case BLUEPRINT -> Material.PAPER;
            case MATERIAL -> Material.IRON_INGOT;
            case RECIPE -> Material.BOOK;
        };
    }

    private Material nodeMaterial(Object value) {
        if (value instanceof Map<?, ?>) {
            return Material.CHEST;
        }
        if (value instanceof List<?>) {
            return Material.BARREL;
        }
        String text = Texts.toStringSafe(value);
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Material.LEVER;
        }
        try {
            Double.parseDouble(text);
            return Material.CLOCK;
        } catch (Exception ignored) {
        }
        return Material.NAME_TAG;
    }

    private String nodeType(Object value) {
        if (value instanceof Map<?, ?>) {
            return "map";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        return "scalar";
    }

    private String path(List<String> path) {
        return path == null || path.isEmpty() ? "<root>" : String.join(".", path);
    }

    private String brief(String text) {
        String value = Texts.normalizeWhitespace(text);
        return value.length() <= 80 ? value : value.substring(0, 77) + "...";
    }

    private String titlePart(String text) {
        String value = Texts.stripMiniTags(text);
        return value.length() <= 18 ? value : value.substring(0, 18);
    }

    private List<String> append(List<String> path, String token) {
        List<String> out = new ArrayList<>(path == null ? List.of() : path);
        out.add(token);
        return out;
    }

    private int parseIndex(String token) {
        try {
            return Integer.parseInt(token.substring(1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private FieldInput parseFieldInput(String input) {
        if (Texts.isBlank(input)) {
            return null;
        }
        String raw = input.trim();
        int split = raw.indexOf('=');
        if (split < 0) {
            split = raw.indexOf(':');
        }
        String key = split < 0 ? raw : raw.substring(0, split).trim();
        String valueText = split < 0 ? "" : raw.substring(split + 1).trim();
        if (Texts.isBlank(key)) {
            return null;
        }
        Object value = switch (valueText) {
            case "{}" -> new LinkedHashMap<String, Object>();
            case "[]" -> new ArrayList<Object>();
            default -> valueText;
        };
        return new FieldInput(key, value);
    }

    private final class Handler implements GuiSessionHandler {

        private final EditorSession session;

        private Handler(EditorSession session) {
            this.session = session;
        }

        @Override
        public void onSlotClick(GuiSession guiSession, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null) {
                return;
            }
            EditorSession.ClickAction action = session.clickActions().get(slot.inventorySlot());
            if (action != null) {
                action.execute(session, event);
            }
        }

        @Override
        public void onClose(GuiSession guiSession, InventoryCloseEvent event) {
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
                plugin.getServer().getScheduler().runTask(plugin, () -> openSession(session));
                return;
            }
            stateManager.remove(session.playerId());
        }
    }
}
