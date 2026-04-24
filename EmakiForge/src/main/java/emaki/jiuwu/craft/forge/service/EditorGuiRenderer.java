package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.text.Texts;

final class EditorGuiRenderer {

    private static final List<Integer> CONTENT = slots(0, 45);
    private static final List<Integer> ALL = slots(0, 54);

    private final EditorGuiService service;
    private final ConfiguredGuiSupport guiSupport;
    private final EditorFieldResolver fieldResolver;
    private final EditorGuiInteractionHandler interactionHandler;
    private final String guiId;

    EditorGuiRenderer(EditorGuiService service,
            ConfiguredGuiSupport guiSupport,
            EditorFieldResolver fieldResolver,
            EditorGuiInteractionHandler interactionHandler,
            String guiId) {
        this.service = service;
        this.guiSupport = guiSupport;
        this.fieldResolver = fieldResolver;
        this.interactionHandler = interactionHandler;
        this.guiId = guiId;
    }

    GuiTemplate createTemplate() {
        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        slots.put("editor", new GuiSlot("editor", ALL, "editor", "GRAY_STAINED_GLASS_PANE", ItemComponentParser.empty(), Map.of()));
        return new GuiTemplate(guiId, "<gold>{title}</gold>", 6, slots);
    }

    String title(EditorSession session) {
        String fallback = switch (session.mode()) {
            case INDEX -> "锻造编辑器";
            case LIST -> session.resourceType() == null ? "资源列表" : session.resourceType().displayName() + "列表";
            case DOCUMENT -> session.resourceType() == null ? "资源编辑" : session.resourceType().displayName() + ":" + fieldResolver.titlePart(session.currentId());
            case SLOT_GRID -> "槽位网格";
            case CONFIRM_CLOSE -> "确认关闭";
        };
        return guiSupport.text(
                guiId,
                "pages." + pageKey(session) + ".title",
                fallback,
                Map.of(
                        "type_name", session.resourceType() == null ? "" : session.resourceType().displayName(),
                        "current_id", Texts.toStringSafe(session.currentId()),
                        "path", fieldResolver.path(session.currentPath())
                )
        );
    }

    ItemStack render(EditorSession session, int slot) {
        return switch (session.mode()) {
            case INDEX -> renderIndex(session, slot);
            case LIST -> renderList(session, slot);
            case DOCUMENT -> renderDocument(session, slot);
            case SLOT_GRID -> renderGrid(session, slot);
            case CONFIRM_CLOSE -> renderConfirm(session, slot);
        };
    }

    private ItemStack renderIndex(EditorSession session, int slot) {
        if (buttonSlot(session, "recipe", slot)) {
            return clickable(session, slot, typeItem(EditableResourceType.RECIPE),
                    (editorSession, event) -> interactionHandler.showType(editorSession, EditableResourceType.RECIPE));
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", Map.of(), "BARRIER", components(
                    "<red>关闭编辑器</red>", List.of("<gray>关闭当前界面。</gray>"))),
                    (editorSession, event) -> interactionHandler.closeSession(editorSession));
        }
        return null;
    }

    private ItemStack renderList(EditorSession session, int slot) {
        EditableResourceType type = session.resourceType();
        if (type == null) {
            session.setMode(EditorSession.Mode.INDEX);
            return renderIndex(session, slot);
        }
        List<String> ids = fieldResolver.ids(type);
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
                    (editorSession, event) -> service.openExisting((Player) event.getWhoClicked(), type, id));
        }
        Map<String, Object> replacements = Map.of(
                "page", session.page() + 1,
                "pages", pages,
                "type_name", type.displayName(),
                "count", ids.size()
        );
        if (buttonSlot(session, "back", slot)) {
            return clickable(session, slot, buttonItem(session, "back", replacements, "COMPASS", components(
                    "<yellow>返回分类</yellow>", List.of("<gray>回到资源类型选择。</gray>"))),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.INDEX);
                        editorSession.setPage(0);
                        editorSession.setResourceType(null);
                        service.openSession(editorSession);
                    });
        }
        if (buttonSlot(session, "prev_page", slot)) {
            return buildPrevPageButton(session, slot, replacements);
        }
        if (buttonSlot(session, "create", slot)) {
            return clickable(session, slot, buttonItem(session, "create", replacements, "LIME_DYE", components(
                    "<green>创建新{type_name}</green>", List.of("<gray>创建新的资源草稿。</gray>"))),
                    (editorSession, event) -> service.createNew((Player) event.getWhoClicked(), type, null));
        }
        if (buttonSlot(session, "summary", slot)) {
            return buttonItem(session, "summary", replacements, "BOOK", components(
                    "<gold>{type_name}列表</gold>", List.of("<gray>总数: {count}</gray>")));
        }
        if (buttonSlot(session, "next_page", slot)) {
            return buildNextPageButton(session, slot, replacements, pages);
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", replacements, "BARRIER", components(
                    "<red>关闭编辑器</red>", List.of("<gray>关闭当前界面。</gray>"))),
                    (editorSession, event) -> interactionHandler.closeSession(editorSession));
        }
        return null;
    }

    private ItemStack renderDocument(EditorSession session, int slot) {
        Object node = session.currentNode() == null ? session.rootData() : session.currentNode();
        List<EditorFieldResolver.Entry> entries = fieldResolver.entries(session, node);
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
            EditorFieldResolver.Entry entry = entries.get(actual);
            return clickable(session, slot, nodeItem(session, entry),
                    (editorSession, event) -> interactionHandler.handleEntry(editorSession, entry, event));
        }
        String sourceKey = fieldResolver.sourceField(node);
        boolean slotList = EditorSchemaSupport.isSlotListPath(session.resourceType(), session.currentPath());
        Map<String, Object> replacements = Map.of(
                "page", session.page() + 1,
                "pages", pages,
                "path", fieldResolver.path(session.currentPath()),
                "node_type", fieldResolver.nodeType(node),
                "source_key", sourceKey == null ? "" : sourceKey
        );
        if (buttonSlot(session, "back", slot)) {
            return clickable(session, slot, buttonItem(session, "back", replacements, "COMPASS", components(
                    "<yellow>返回上级</yellow>", List.of("<gray>{path}</gray>"))),
                    (editorSession, event) -> interactionHandler.goBack(editorSession));
        }
        if (buttonSlot(session, "prev_page", slot)) {
            return buildPrevPageButton(session, slot, replacements);
        }
        if (buttonSlot(session, "add", slot)) {
            return clickable(session, slot, buttonItem(session, "add", replacements, "LIME_DYE", components(
                    "<green>新增条目</green>", List.of("<gray>Map 新增字段，List 追加元素。</gray>"))),
                    (editorSession, event) -> interactionHandler.addNode(editorSession));
        }
        if (buttonSlot(session, "save", slot)) {
            return clickable(session, slot, buttonItem(session, "save", replacements, "EMERALD", components(
                    "<green>保存并热重载</green>", List.of("<gray>保存当前资源。</gray>"))),
                    (editorSession, event) -> interactionHandler.save(editorSession));
        }
        if (buttonSlot(session, "context", slot)) {
            if (sourceKey == null) {
                return buttonItem(session, "context", replacements, "WRITABLE_BOOK", components(
                        "<gold>当前节点</gold>", List.of("<gray>{path}</gray>", "<gray>{node_type}</gray>")));
            }
            return clickable(session, slot, buttonItem(session, "context_source", replacements, "ITEM_FRAME", components(
                    "<gold>读取主手物品</gold>", List.of("<gray>写入 {source_key} 字段。</gray>"))),
                    (editorSession, event) -> interactionHandler.applyHeldSource(editorSession, editorSession.currentPath(), sourceKey));
        }
        if (buttonSlot(session, "grid", slot)) {
            if (!slotList) {
                return null;
            }
            return clickable(session, slot, buttonItem(session, "grid", replacements, "CHEST", components(
                    "<aqua>打开槽位网格</aqua>", List.of("<gray>切换当前配方槽位。</gray>"))),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.SLOT_GRID);
                        editorSession.setPage(0);
                        service.openSession(editorSession);
                    });
        }
        if (buttonSlot(session, "delete", slot)) {
            return clickable(session, slot, buttonItem(session, "delete", replacements, "TNT", components(
                    "<red>删除当前资源</red>", List.of("<gray>会先备份再删除。</gray>"))),
                    (editorSession, event) -> interactionHandler.delete(editorSession));
        }
        if (buttonSlot(session, "next_page", slot)) {
            return buildNextPageButton(session, slot, replacements, pages);
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", replacements, "BARRIER", components(
                    "<red>关闭编辑器</red>", List.of("<gray>未保存更改会先确认。</gray>"))),
                    (editorSession, event) -> interactionHandler.requestClose(editorSession));
        }
        return null;
    }

    private ItemStack renderGrid(EditorSession session, int slot) {
        List<Integer> values = fieldResolver.toIntList(session.currentNode());
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
                        service.openSession(editorSession);
                    });
        }
        Map<String, Object> replacements = Map.of(
                "page", session.page() + 1,
                "pages", pages,
                "count", values.size(),
                "path", fieldResolver.path(session.currentPath())
        );
        if (buttonSlot(session, "back_document", slot)) {
            return clickable(session, slot, buttonItem(session, "back_document", replacements, "COMPASS", components(
                    "<yellow>返回文档</yellow>", List.of("<gray>回到文档树。</gray>"))),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.DOCUMENT);
                        editorSession.setPage(0);
                        service.openSession(editorSession);
                    });
        }
        if (buttonSlot(session, "prev_page", slot)) {
            return buildPrevPageButton(session, slot, replacements);
        }
        if (buttonSlot(session, "save", slot)) {
            return clickable(session, slot, buttonItem(session, "save", replacements, "EMERALD", components(
                    "<green>保存并热重载</green>", List.of("<gray>直接保存当前配方。</gray>"))),
                    (editorSession, event) -> interactionHandler.save(editorSession));
        }
        if (buttonSlot(session, "summary", slot)) {
            return buttonItem(session, "summary", replacements, "BOOK", components(
                    "<gold>槽位数量: {count}</gold>", List.of("<gray>{path}</gray>")));
        }
        if (buttonSlot(session, "next_page", slot)) {
            return buildNextPageButton(session, slot, replacements, pages);
        }
        if (buttonSlot(session, "close", slot)) {
            return clickable(session, slot, buttonItem(session, "close", replacements, "BARRIER", components(
                    "<red>关闭编辑器</red>", List.of("<gray>未保存更改会先确认。</gray>"))),
                    (editorSession, event) -> interactionHandler.requestClose(editorSession));
        }
        return null;
    }

    private ItemStack renderConfirm(EditorSession session, int slot) {
        if (buttonSlot(session, "save_exit", slot)) {
            return clickable(session, slot, buttonItem(session, "save_exit", Map.of(), "EMERALD", components(
                    "<green>保存并退出</green>", List.of("<gray>保存资源后关闭。</gray>"))),
                    (editorSession, event) -> interactionHandler.save(editorSession));
        }
        if (buttonSlot(session, "discard", slot)) {
            return clickable(session, slot, buttonItem(session, "discard", Map.of(), "LAVA_BUCKET", components(
                    "<red>放弃修改</red>", List.of("<gray>丢弃当前更改。</gray>"))),
                    (editorSession, event) -> interactionHandler.forceClose(editorSession));
        }
        if (buttonSlot(session, "return", slot)) {
            return clickable(session, slot, buttonItem(session, "return", Map.of(), "COMPASS", components(
                    "<yellow>返回编辑</yellow>", List.of("<gray>回到当前文档。</gray>"))),
                    (editorSession, event) -> {
                        editorSession.setMode(EditorSession.Mode.DOCUMENT);
                        service.openSession(editorSession);
                    });
        }
        return null;
    }

    private List<Integer> contentSlots(EditorSession session) {
        return guiSupport.slots(guiId, "pages." + pageKey(session) + ".content_slots", CONTENT);
    }

    private boolean buttonSlot(EditorSession session, String buttonKey, int slot) {
        return guiSupport.slots(
                guiId,
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
                guiId,
                "pages." + pageKey(session) + ".buttons." + buttonKey,
                replacements,
                fallbackItem,
                fallbackComponents
        );
    }

    private ItemStack gridItem(boolean enabled, Map<String, ?> replacements) {
        return guiSupport.build(
                guiId,
                "pages.grid.content_templates." + (enabled ? "enabled" : "disabled"),
                replacements,
                enabled ? "LIME_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE",
                components(
                        (enabled ? "<green>" : "<gray>") + "槽位 {slot}" + (enabled ? " 已启用</green>" : " 未启用</gray>"),
                        List.of("<gray>左键切换该槽位。</gray>")
                )
        );
    }

    private ItemStack typeItem(EditableResourceType type) {
        String key = switch (type) {
            case BLUEPRINT -> "blueprint";
            case MATERIAL -> "material";
            case RECIPE -> "recipe";
        };
        return guiSupport.build(
                guiId,
                "pages.index.buttons." + key,
                Map.of("type_name", type.displayName()),
                typeIcon(type).name(),
                components(
                        "<gold>{type_name}</gold>",
                        List.of("<gray>打开{type_name}资源列表。</gray>")
                )
        );
    }

    private ItemStack resourceItem(EditableResourceType type, String id) {
        return guiSupport.build(
                guiId,
                "pages.list.content_item",
                Map.of(
                        "entry_id", id,
                        "entry_name", fieldResolver.resourceName(type, id),
                        "type_name", type.displayName()
                ),
                typeIcon(type).name(),
                components(
                        "<gold>{entry_id}</gold>",
                        List.of("<gray>{entry_name}</gray>", "<gray>左键打开编辑。</gray>")
                )
        );
    }

    private ItemStack nodeItem(EditorSession session, EditorFieldResolver.Entry entry) {
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
            } catch (Exception _) {
                templateKey = "scalar";
                fallbackItem = "NAME_TAG";
            }
        }
        return guiSupport.build(
                guiId,
                "pages.document.content_templates." + templateKey,
                Map.of(
                        "entry_label", entry.label(),
                        "node_type", fieldResolver.nodeType(value),
                        "node_value", fieldResolver.brief(EditorSchemaSupport.summarize(value)),
                        "is_slot_list", EditorSchemaSupport.isSlotListPath(session.resourceType(), entry.path()) ? "<aqua>支持槽位网格模式</aqua>" : "",
                        "edit_hint", value instanceof Map<?, ?> || value instanceof List<?> ? "<yellow>左键进入子节点</yellow>" : "<yellow>左键聊天输入</yellow>",
                        "delete_hint", value instanceof Map<?, ?> || value instanceof List<?> || EditorSchemaSupport.isItemSourcePath(entry.path())
                                ? "<red>Shift+右键删除</red>"
                                : "<red>右键删除</red>",
                        "secondary_hint", EditorSchemaSupport.isItemSourcePath(entry.path()) ? "<aqua>右键读取主手物品</aqua>" : ""
                ),
                fallbackItem,
                components(
                        "<gold>{entry_label}</gold>",
                        List.of(
                                "<gray>类型: {node_type}</gray>",
                                "<gray>值: {node_value}</gray>",
                                "{edit_hint}",
                                "{secondary_hint}",
                                "{delete_hint}",
                                "{is_slot_list}"
                        )
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
                case "recipe" -> List.of(22);
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

    private ItemStack clickable(EditorSession session, int slot, ItemStack item, EditorSession.ClickAction action) {
        if (action != null) {
            session.clickActions().put(slot, action);
        }
        return item;
    }

    private ItemStack buildPrevPageButton(EditorSession session, int slot, Map<String, ?> replacements) {
        return clickable(session, slot, buttonItem(session, "prev_page", replacements, "ARROW", components(
                "<yellow>上一页</yellow>", List.of("<gray>{page}/{pages}</gray>"))),
                (editorSession, event) -> {
                    if (editorSession.page() > 0) {
                        editorSession.setPage(editorSession.page() - 1);
                        service.openSession(editorSession);
                    }
                });
    }

    private ItemStack buildNextPageButton(EditorSession session, int slot, Map<String, ?> replacements, int maxPages) {
        return clickable(session, slot, buttonItem(session, "next_page", replacements, "ARROW", components(
                "<yellow>下一页</yellow>", List.of("<gray>{page}/{pages}</gray>"))),
                (editorSession, event) -> {
                    if (editorSession.page() + 1 < maxPages) {
                        editorSession.setPage(editorSession.page() + 1);
                        service.openSession(editorSession);
                    }
                });
    }

    private Material typeIcon(EditableResourceType type) {
        return switch (type) {
            case BLUEPRINT -> Material.PAPER;
            case MATERIAL -> Material.IRON_INGOT;
            case RECIPE -> Material.BOOK;
        };
    }

    private static ItemComponentParser.ItemComponents components(String name, List<String> lore) {
        return new ItemComponentParser.ItemComponents(name, true, lore, null, null, Map.of(), List.of());
    }

    private static List<Integer> slots(int from, int to) {
        List<Integer> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            out.add(i);
        }
        return List.copyOf(out);
    }
}
