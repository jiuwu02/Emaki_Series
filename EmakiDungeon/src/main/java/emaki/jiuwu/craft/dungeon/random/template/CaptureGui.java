package emaki.jiuwu.craft.dungeon.random.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.dungeon.editor.EditorModules;
import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;

public final class CaptureGui implements Listener {

    static final List<String> NODE_MODULES = List.of(
            "spawn_point", "mob_spawn_points", "respawn_points", "check_points", "exit_points", "block_points",
            "reward_points", "option_points", "tip_points", "teleport_zones", "boundary_zones",
            "effect_zones", "mechanism_zones", "room_timer_zones", "item_points", "fx_points",
            "temp_items", "kill_targets");

    private enum Panel {
        MAIN, META, ANCHORS, DOOR, NODES, NODE, PLACE, STRUCTURE, CREATE
    }

    private static final class Holder implements org.bukkit.inventory.InventoryHolder {

        final Panel panel;
        final String[] path;
        final int page;

        Holder(Panel panel, String[] path, int page) {
            this.panel = panel;
            this.path = path;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private String titleKey(Panel panel) {
        return switch (panel) {
            case MAIN -> "random.gui.title_main";
            case META -> "random.gui.title_meta";
            case ANCHORS -> "random.gui.title_anchors";
            case DOOR -> "random.gui.title_anchors";
            case NODES -> "random.gui.title_nodes";
            case NODE -> "random.gui.title_node";
            case PLACE -> "random.gui.title_place";
            case STRUCTURE -> "random.gui.title_structure";
            case CREATE -> "random.gui.title_create";
        };
    }

    private Map<String, String> titleArgs(CaptureService.Session session, Holder holder) {
        Map<String, String> args = new java.util.LinkedHashMap<>();
        String region = switch (holder.panel) {
            case STRUCTURE -> holder.path.length > 0 ? holder.path[0] : "";
            case PLACE -> holder.path.length > 0 && !holder.path[0].isBlank()
                    ? holder.path[0] : captures.targetOf(session);
            case NODE -> holder.path.length > 1 && !holder.path[0].isBlank()
                    ? holder.path[0] : captures.targetOf(session);
            default -> captures.targetOf(session);
        };
        if (region == null || region.isBlank()) {
            region = messages.message("random.gui.title_no_region", Map.of());
        }
        args.put("region", region);
        args.put("id", region);
        return args;
    }

    private static final int SLOT_PREV = 45;
    private static final int SLOT_ACTION = 46;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_INFO = 50;
    private static final int SLOT_CLOSE = 53;
    private static final int SLOT_POS1 = 10;
    private static final int SLOT_POS2 = 11;
    private static final int SLOT_META = 12;
    private static final int SLOT_ANCHORS = 13;
    private static final int SLOT_NODES = 14;
    private static final int SLOT_SAVE = 16;
    private static final int SLOT_STATUS = 22;
    private static final int SLOT_DELETE = 48;

    private final Plugin plugin;
    private final CaptureService captures;
    private final MessageService messages;
    private final ChatInputService chatInput;
    private java.util.function.Function<String, RandomConfig> configSupplier;
    private volatile java.util.function.Consumer<java.util.UUID> editorRefresh;
    private volatile emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader templates;

    public CaptureGui(Plugin plugin, CaptureService captures, MessageService messages, ChatInputService chatInput) {
        this.plugin = plugin;
        this.captures = captures;
        this.messages = messages;
        this.chatInput = chatInput;
    }

    /** 按副本 ID 取该副本 world config 的 random 段配置。 */
    public void bindConfigSupplier(java.util.function.Function<String, RandomConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    private RandomConfig configOf(CaptureService.Session session) {
        if (configSupplier == null || session == null || session.worldName == null
                || !session.worldName.startsWith(CaptureService.EDIT_WORLD_PREFIX)) {
            return null;
        }
        return configSupplier.apply(
                session.worldName.substring(CaptureService.EDIT_WORLD_PREFIX.length()));
    }

    public void bindEditorRefresh(java.util.function.Consumer<java.util.UUID> editorRefresh) {
        this.editorRefresh = editorRefresh;
    }

    public void bindTemplates(emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader templates) {
        this.templates = templates;
    }

    private String templateIdOf(Panel panel) {
        return switch (panel) {
            case MAIN -> "capture_main";
            case META -> "capture_meta";
            case ANCHORS, DOOR -> "capture_anchors";
            case NODES -> "capture_nodes";
            case NODE -> "capture_node";
            case PLACE -> "capture_place";
            case STRUCTURE -> "capture_structure";
            case CREATE -> "capture_create";
        };
    }

    emaki.jiuwu.craft.corelib.gui.GuiTemplate templateOf(Panel panel) {
        emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader loader = templates;
        return loader == null ? null : loader.get(templateIdOf(panel));
    }

    int slotOf(Panel panel, String key, int fallback) {
        emaki.jiuwu.craft.corelib.gui.GuiTemplate template = templateOf(panel);
        emaki.jiuwu.craft.corelib.gui.GuiSlot slot = template == null ? null : template.slot(key);
        if (slot == null || slot.slots().isEmpty()) {
            return fallback;
        }
        return slot.slots().get(0);
    }

    private void refreshEditorMarkers(CaptureService.Session session) {
        java.util.function.Consumer<java.util.UUID> hook = editorRefresh;
        if (hook != null && session != null && session.playerId != null) {
            hook.accept(session.playerId);
        }
    }

    public void openMain(Player player) {
        open(player, new Holder(Panel.MAIN, new String[0], 0));
    }

    public void openNode(Player player, ModuleNode node) {
        open(player, new Holder(Panel.NODE, new String[] {"", node.nodeId()}, 0));
    }

    public void openStructureNode(Player player, String templateId, String nodeId) {
        open(player, new Holder(Panel.NODE, new String[] {templateId, nodeId}, 0));
    }

    public void openStructure(Player player, String templateId) {
        open(player, new Holder(Panel.STRUCTURE, new String[] {templateId}, 0));
    }

    public void openPlacement(Player player, Block block) {
        openPlacement(player, block, "");
    }

    public void openPlacement(Player player, Block block, String templateId) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.pendingBlock = block;
        if (templateId != null && !templateId.isBlank()) {
            session.targetId = templateId;
        }
        open(player, new Holder(Panel.PLACE, new String[] {templateId == null ? "" : templateId}, 0));
    }

    private void open(Player player, Holder holder) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        Map<String, String> args = titleArgs(session, holder);
        emaki.jiuwu.craft.corelib.gui.GuiTemplate template = templateOf(holder.panel);
        String title = template == null
                ? messages.message(titleKey(holder.panel), args)
                : template.title().replace("%region%", args.getOrDefault("region", ""))
                        .replace("%id%", args.getOrDefault("id", ""));
        int rows = template == null || !template.supportsRows() ? 6 : template.rows();
        Inventory inventory = plugin.getServer().createInventory(holder, rows * 9,
                MiniMessages.parse(title));
        render(inventory, session, holder);
        player.openInventory(inventory);
    }

    private void render(Inventory inventory, CaptureService.Session session, Holder holder) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
        switch (holder.panel) {
            case MAIN -> renderMain(inventory, session);
            case META -> renderMeta(inventory, session);
            case ANCHORS -> renderAnchors(inventory, session, holder);
            case DOOR -> renderDoor(inventory, session, holder);
            case NODES -> renderNodes(inventory, session, holder);
            case NODE -> renderNode(inventory, session, holder);
            case PLACE -> renderPlace(inventory, holder);
            case STRUCTURE -> renderStructure(inventory, session, holder);
            case CREATE -> renderCreate(inventory, session);
        }
    }

    private void renderMain(Inventory inventory, CaptureService.Session session) {
        emaki.jiuwu.craft.corelib.gui.GuiTemplate template = templateOf(Panel.MAIN);
        inventory.setItem(slotOf(Panel.MAIN, "pos1", SLOT_POS1), item(Material.LIME_TERRACOTTA,
                "random.gui.pos1", List.of(coordLine(session.pos1))));
        inventory.setItem(slotOf(Panel.MAIN, "pos2", SLOT_POS2), item(Material.LIME_TERRACOTTA,
                "random.gui.pos2", List.of(coordLine(session.pos2))));
        inventory.setItem(slotOf(Panel.MAIN, "meta", SLOT_META),
                item(Material.BOOK, "random.gui.meta", List.of()));
        inventory.setItem(slotOf(Panel.MAIN, "anchors", SLOT_ANCHORS),
                item(Material.IRON_DOOR, "random.gui.anchors",
                        List.of(String.valueOf(captures.doorsOf(session).size()))));
        inventory.setItem(slotOf(Panel.MAIN, "nodes", SLOT_NODES),
                item(Material.CHEST, "random.gui.nodes",
                        List.of(String.valueOf(session.nodes.size()))));
        inventory.setItem(slotOf(Panel.MAIN, "create_region", SLOT_SAVE),
                item(Material.LIME_CONCRETE, "random.gui.create_region", List.of()));
        inventory.setItem(slotOf(Panel.MAIN, "status", SLOT_STATUS),
                item(Material.ENDER_EYE, "random.gui.status",
                        List.of(session.structureType, String.join(",", session.tags),
                                String.valueOf(session.pos1 != null && session.pos2 != null))));
        inventory.setItem(slotOf(Panel.MAIN, "close", SLOT_CLOSE),
                item(Material.BARRIER, "random.gui.close", List.of()));
    }

    private void renderMeta(Inventory inventory, CaptureService.Session session) {
        inventory.setItem(10, item(Material.PAPER, "random.gui.meta_type", List.of(session.structureType)));
        inventory.setItem(11, item(Material.NAME_TAG, "random.gui.meta_tags",
                List.of(String.join(",", session.tags))));
        inventory.setItem(12, item(Material.LADDER, "random.gui.meta_floors",
                List.of(String.valueOf(metaFloorsOf(session)), messages.message("random.gui.meta_floors_hint", Map.of()))));
        inventory.setItem(13, item(Material.GLASS_PANE, "random.gui.meta_mirrorable",
                List.of(String.valueOf(session.mirrorable))));
        inventory.setItem(14, item(Material.COMPASS, "random.gui.meta_rotatable",
                List.of(String.valueOf(session.rotatable))));
        inventory.setItem(15, item(Material.SHIELD, "random.gui.meta_intrusion", List.of(session.intrusionPolicy)));
        inventory.setItem(16, item(Material.OAK_DOOR, "random.gui.meta_door", List.of(session.doorPolicy)));
        inventory.setItem(17, item(Material.BRICKS, "random.gui.meta_seal", List.of(session.sealBlock)));
        inventory.setItem(18, item(Material.GOLD_INGOT, "random.gui.meta_weight",
                List.of(String.valueOf(session.weight))));
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderAnchors(Inventory inventory, CaptureService.Session session, Holder holder) {
        List<DoorAnchor> doors = captures.doorsOf(session);
        int start = holder.page * 36;
        for (int index = 0; index < 36 && start + index < doors.size(); index++) {
            DoorAnchor anchor = doors.get(start + index);
            inventory.setItem(index, itemRaw(Material.IRON_DOOR,
                    "<white>" + plain(anchor.id()) + "</white>",
                    List.of(anchor.facing() + " / " + anchor.connectionKind(),
                            anchor.width() + "x" + anchor.height(),
                            "floor-delta=" + anchor.floorDelta() + ", vertical-delta=" + anchor.verticalDelta())));
        }
        renderNav(inventory, holder, doors.size(), Material.LIME_DYE, "random.gui.door_add");
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderNodes(Inventory inventory, CaptureService.Session session, Holder holder) {
        int start = holder.page * 36;
        for (int index = 0; index < 36 && start + index < session.nodes.size(); index++) {
            ModuleNode node = session.nodes.get(start + index);
            inventory.setItem(index, itemRaw(Material.CHEST,
                    "<white>" + plain(node.moduleType()) + "</white> <gray>" + plain(node.nodeId()) + "</gray>",
                    List.of()));
        }
        renderNav(inventory, holder, session.nodes.size(), Material.COMPASS, "random.gui.nodes_hint");
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderNode(Inventory inventory, CaptureService.Session session, Holder holder) {
        String templateId = holder.path[0];
        String nodeId = holder.path[1];
        ModuleNode node = nodeOf(session, templateId, nodeId);
        if (node == null) {
            inventory.setItem(SLOT_INFO, item(Material.BARRIER, "random.gui.structure_missing", List.of()));
            inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
            return;
        }
        inventory.setItem(SLOT_INFO, itemRaw(Material.BOOK,
                "<white>" + plain(node.moduleType()) + "</white> <gray>"
                        + plain(node.nodeId()) + "</gray>", List.of()));
        if (templateId.isBlank()) {
            int slot = 0;
            for (Map.Entry<String, Object> entry : node.params().entrySet()) {
                if (slot >= 36) {
                    break;
                }
                inventory.setItem(slot++, itemRaw(Material.PAPER,
                        "<white>" + plain(entry.getKey()) + "</white> <gray>"
                                + plain(String.valueOf(entry.getValue())) + "</gray>", List.of()));
            }
        } else {
            List<EditorModules.Field> fields = fieldsOf(node);
            for (int index = 0; index < fields.size() && index < 36; index++) {
                EditorModules.Field field = fields.get(index);
                Object value = valueOf(node, field);
                inventory.setItem(index, itemRaw(Material.PAPER,
                        "<white>" + plain(fieldLabel(field)) + "</white> <gray>"
                                + plain(value == null ? "-" : String.valueOf(value)) + "</gray>", List.of()));
            }
        }
        inventory.setItem(SLOT_DELETE, item(Material.RED_CONCRETE, "random.gui.node_delete", List.of()));
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private ModuleNode nodeOf(CaptureService.Session session, String templateId, String nodeId) {
        if (templateId.isBlank()) {
            for (ModuleNode node : session.nodes) {
                if (node.nodeId().equals(nodeId)) {
                    return node;
                }
            }
            return null;
        }
        return captures.structureNode(session, templateId, nodeId);
    }

    private List<EditorModules.Field> fieldsOf(ModuleNode node) {
        EditorModules.ModuleSpec spec = specOf(node.moduleType());
        return spec == null ? List.of() : spec.fields();
    }

    private EditorModules.ModuleSpec specOf(String moduleType) {
        if (moduleType == null) {
            return null;
        }
        for (EditorModules.ModuleSpec spec : EditorModules.all()) {
            if (spec.section().equals(moduleType)) {
                return spec;
            }
        }
        return null;
    }

    private String fieldLabel(EditorModules.Field field) {
        return messages.message(fieldKey(field.key()), Map.of());
    }

    private String fieldKey(String key) {
        return "editor.gui.field." + key;
    }

    private Object valueOf(ModuleNode node, EditorModules.Field field) {
        Object current = node.params();
        for (String segment : field.path()) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private void renderPlace(Inventory inventory, Holder holder) {
        boolean structure = holder.path.length > 0 && !holder.path[0].isBlank();
        for (int index = 0; index < NODE_MODULES.size() && index < 45; index++) {
            inventory.setItem(index, itemRaw(Material.LIME_DYE,
                    "<white>" + plain(NODE_MODULES.get(index)) + "</white>", List.of()));
        }
        if (structure) {
            inventory.setItem(SLOT_INFO, item(Material.PAPER, "random.gui.place_structure_hint",
                    List.of(holder.path[0])));
        }
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderCreate(Inventory inventory, CaptureService.Session session) {
        inventory.setItem(10, item(Material.LIGHT_BLUE_CONCRETE, "random.gui.create_room",
                List.of(TemplateModel.TYPE_ROOM.equals(session.structureType) ? "*" : "")));
        inventory.setItem(11, item(Material.ORANGE_CONCRETE, "random.gui.create_corridor",
                List.of(TemplateModel.TYPE_CORRIDOR.equals(session.structureType) ? "*" : "")));
        inventory.setItem(12, item(Material.LADDER, "random.gui.create_transition",
                List.of(TemplateModel.TYPE_TRANSITION.equals(session.structureType) ? "*" : "")));
        inventory.setItem(SLOT_INFO, item(Material.PAPER, "random.gui.create_hint", List.of()));
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderStructure(Inventory inventory, CaptureService.Session session, Holder holder) {
        TemplateMeta meta = captures.structure(session, holder.path[0]);
        if (meta == null) {
            inventory.setItem(SLOT_INFO, item(Material.BARRIER, "random.gui.structure_missing", List.of()));
            inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
            return;
        }
        inventory.setItem(10, item(Material.PAPER, "random.gui.meta_type", List.of(meta.structureType())));
        inventory.setItem(11, item(Material.NAME_TAG, "random.gui.meta_tags",
                List.of(String.join(",", meta.tags()))));
        inventory.setItem(12, item(Material.LADDER, "random.gui.meta_floors",
                List.of(String.valueOf(meta.doorFloors()), messages.message("random.gui.meta_floors_hint", Map.of()))));
        inventory.setItem(13, item(Material.GLASS_PANE, "random.gui.meta_mirrorable",
                List.of(String.valueOf(meta.mirrorable()))));
        inventory.setItem(14, item(Material.COMPASS, "random.gui.meta_rotatable",
                List.of(String.valueOf(meta.rotatable()))));
        inventory.setItem(15, item(Material.SHIELD, "random.gui.meta_intrusion", List.of(meta.intrusionPolicy())));
        inventory.setItem(16, item(Material.OAK_DOOR, "random.gui.meta_door", List.of(meta.doorPolicy())));
        inventory.setItem(17, item(Material.BRICKS, "random.gui.meta_seal", List.of(meta.sealBlock())));
        inventory.setItem(18, item(Material.GOLD_INGOT, "random.gui.meta_weight",
                List.of(String.valueOf(meta.weight()))));
        inventory.setItem(SLOT_DELETE, item(Material.RED_CONCRETE, "random.gui.structure_delete", List.of()));
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderNav(Inventory inventory, Holder holder, int total, Material actionMaterial, String actionKey) {
        if (holder.page > 0) {
            inventory.setItem(SLOT_PREV, item(Material.ARROW, "random.gui.prev", List.of()));
        }
        inventory.setItem(SLOT_ACTION, item(actionMaterial, actionKey, List.of()));
        if ((holder.page + 1) * 36 < total) {
            inventory.setItem(SLOT_NEXT, item(Material.ARROW, "random.gui.next", List.of()));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }
        handleClick(player, holder, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void handleClick(Player player, Holder holder, int slot) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        switch (holder.panel) {
            case MAIN -> handleMain(player, session, slot);
            case META -> handleMeta(player, holder, session, slot);
            case ANCHORS -> handleAnchors(player, holder, session, slot);
            case DOOR -> handleDoor(player, holder, session, slot);
            case NODES -> handleNodes(player, holder, session, slot);
            case NODE -> handleNode(player, holder, session, slot);
            case PLACE -> handlePlace(player, session, holder, slot);
            case STRUCTURE -> handleStructure(player, holder, session, slot);
            case CREATE -> handleCreate(player, session, slot);
        }
    }

    private void handleMain(Player player, CaptureService.Session session, int slot) {
        switch (slot) {
            case SLOT_POS1 -> {
                report(player, captures.pos(player, 1, configOf(session)));
                reopen(player, new Holder(Panel.MAIN, new String[0], 0));
            }
            case SLOT_POS2 -> {
                report(player, captures.pos(player, 2, configOf(session)));
                reopen(player, new Holder(Panel.MAIN, new String[0], 0));
            }
            case SLOT_META -> open(player, new Holder(Panel.META, new String[0], 0));
            case SLOT_ANCHORS -> open(player, new Holder(Panel.ANCHORS, new String[0], 0));
            case SLOT_NODES -> open(player, new Holder(Panel.NODES, new String[0], 0));
            case SLOT_SAVE -> open(player, new Holder(Panel.CREATE, new String[0], 0));
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleMeta(Player player, Holder holder, CaptureService.Session session, int slot) {
        switch (slot) {
            case 10 -> {
                String next = switch (session.structureType) {
                    case TemplateModel.TYPE_ROOM -> TemplateModel.TYPE_CORRIDOR;
                    case TemplateModel.TYPE_CORRIDOR -> TemplateModel.TYPE_TRANSITION;
                    default -> TemplateModel.TYPE_ROOM;
                };
                report(player, captures.meta(player, "type", next));
                reopen(player, holder);
            }
            case 11 -> promptClose(player, "tags", text -> report(player, captures.meta(player, "tags", text)));
            case 12 -> {
                report(player, captures.meta(player, "floors", String.valueOf(metaFloorsOf(session))));
                reopen(player, holder);
            }
            case 13 -> {
                report(player, captures.meta(player, "mirrorable", String.valueOf(!session.mirrorable)));
                reopen(player, holder);
            }
            case 14 -> prompt(player, holder, "rotations", text -> {
                report(player, captures.meta(player, "rotations", text));
                reopen(player, holder);
            });
            case 15 -> {
                String next = TemplateModel.INTRUSION_REJECT.equals(session.intrusionPolicy)
                        ? TemplateModel.INTRUSION_ALLOW : TemplateModel.INTRUSION_REJECT;
                report(player, captures.meta(player, "intrusion-policy", next));
                reopen(player, holder);
            }
            case 16 -> {
                String next = switch (session.doorPolicy) {
                    case TemplateModel.POLICY_SEAL -> TemplateModel.POLICY_KEEP;
                    case TemplateModel.POLICY_KEEP -> TemplateModel.POLICY_REJECT;
                    default -> TemplateModel.POLICY_SEAL;
                };
                report(player, captures.meta(player, "door-policy", next));
                reopen(player, holder);
            }
            case 17 -> promptClose(player, "seal-block", text -> report(player, captures.meta(player, "seal-block", text)));
            case 18 -> promptClose(player, "weight", text -> report(player, captures.meta(player, "weight", text)));
            case SLOT_BACK -> open(player, new Holder(Panel.MAIN, new String[0], 0));
            default -> {
            }
        }
    }

    private void handleDoor(Player player, Holder holder, CaptureService.Session session, int slot) {
        if (slot == SLOT_BACK) {
            open(player, new Holder(Panel.ANCHORS, new String[0], 0));
            return;
        }
        if (slot == SLOT_DELETE) {
            report(player, captures.doorRemove(player, holder.path[1]));
            open(player, new Holder(Panel.ANCHORS, new String[0], 0));
            return;
        }
        DoorAnchor anchor = doorOf(session, holder.path[0], holder.path[1]);
        if (anchor == null) {
            open(player, new Holder(Panel.ANCHORS, new String[0], 0));
            return;
        }
        if (slot == 10) {
            String next = switch (anchor.connectionKind()) {
                case TemplateModel.CONNECTION_WALKWAY -> TemplateModel.CONNECTION_STAIRCASE;
                case TemplateModel.CONNECTION_STAIRCASE -> TemplateModel.CONNECTION_VERTICAL;
                case TemplateModel.CONNECTION_VERTICAL -> TemplateModel.CONNECTION_LADDER;
                case TemplateModel.CONNECTION_LADDER -> TemplateModel.CONNECTION_ELEVATOR;
                default -> TemplateModel.CONNECTION_WALKWAY;
            };
            CaptureService.DoorSettings settings = new CaptureService.DoorSettings(next,
                    anchor.floorDelta(), anchor.verticalDelta());
            if (holder.path[0].isBlank()) {
                captures.pendingDoorSettings(session, settings);
            } else {
                captures.updateDoor(session, holder.path[0], anchor.id(), settings);
            }
            reopen(player, holder);
        } else if (slot == 11 || slot == 12) {
            boolean floor = slot == 11;
            prompt(player, holder, floor ? "floor_delta" : "vertical_delta", text -> {
                String value = text.trim();
                Integer parsed = value.isBlank() || "-".equals(value) ? null : parseInt(value, 0);
                CaptureService.DoorSettings settings = new CaptureService.DoorSettings(anchor.connectionKind(),
                        floor ? parsed : anchor.floorDelta(), floor ? anchor.verticalDelta() : parsed);
                if (holder.path[0].isBlank()) {
                    captures.pendingDoorSettings(session, settings);
                } else {
                    captures.updateDoor(session, holder.path[0], anchor.id(), settings);
                }
                reopen(player, holder);
            });
        }
    }

    private int metaFloorsOf(CaptureService.Session session) {
        java.util.TreeSet<Integer> heights = new java.util.TreeSet<>();
        for (DoorAnchor door : captures.doorsOf(session)) {
            heights.add(door.y());
        }
        return Math.max(1, heights.size());
    }

    private DoorAnchor doorOf(CaptureService.Session session, String templateId, String doorId) {
        for (DoorAnchor anchor : captures.doorsOf(session)) {
            if (anchor.id().equals(doorId)) {
                return anchor;
            }
        }
        return null;
    }

    private void renderDoor(Inventory inventory, CaptureService.Session session, Holder holder) {
        DoorAnchor anchor = doorOf(session, holder.path[0], holder.path[1]);
        if (anchor == null) {
            inventory.setItem(SLOT_INFO, item(Material.BARRIER, "random.gui.structure_missing", List.of()));
        } else {
            inventory.setItem(10, itemRaw(Material.IRON_DOOR, anchor.connectionKind(), List.of()));
            inventory.setItem(11, itemRaw(Material.LADDER, "floor-delta=" + anchor.floorDelta(), List.of()));
            inventory.setItem(12, itemRaw(Material.SCAFFOLDING, "vertical-delta=" + anchor.verticalDelta(), List.of()));
        }
        inventory.setItem(SLOT_DELETE, item(Material.RED_CONCRETE, "random.gui.node_delete", List.of()));
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void handleAnchors(Player player, Holder holder, CaptureService.Session session, int slot) {
        List<DoorAnchor> doors = captures.doorsOf(session);
        if (handleNav(player, holder, doors.size(), slot, Panel.ANCHORS)) {
            return;
        }
        if (slot == SLOT_ACTION) {
            messages.send(player, "random.gui.door_hint", Map.of());
            reopen(player, holder);
            return;
        }
        int start = holder.page * 36;
        if (slot >= 0 && slot < 36 && start + slot < doors.size()) {
            String id = doors.get(start + slot).id();
            open(player, new Holder(Panel.DOOR, new String[] {captures.targetOf(session), id}, 0));
            reopen(player, holder);
            return;
        }
        if (slot == SLOT_BACK) {
            open(player, new Holder(Panel.MAIN, new String[0], 0));
        }
    }

    private void handleNodes(Player player, Holder holder, CaptureService.Session session, int slot) {
        if (handleNav(player, holder, session.nodes.size(), slot, Panel.NODES)) {
            return;
        }
        int start = holder.page * 36;
        if (slot >= 0 && slot < 36 && start + slot < session.nodes.size()) {
            openNode(player, session.nodes.get(start + slot));
            return;
        }
        if (slot == SLOT_BACK) {
            open(player, new Holder(Panel.MAIN, new String[0], 0));
        }
    }

    private void handleNode(Player player, Holder holder, CaptureService.Session session, int slot) {
        String templateId = holder.path[0];
        String nodeId = holder.path[1];
        if (slot == SLOT_BACK) {
            if (templateId.isBlank()) {
                open(player, new Holder(Panel.NODES, new String[0], 0));
            } else {
                open(player, new Holder(Panel.STRUCTURE, new String[] {templateId}, 0));
            }
            return;
        }
        if (slot == SLOT_DELETE) {
            if (templateId.isBlank()) {
                captures.deleteNode(session, nodeId);
                messages.send(player, "editor.module_deleted", Map.of("type", "node", "id", nodeId));
                captures.fireRefresh(session.playerId);
                open(player, new Holder(Panel.NODES, new String[0], 0));
            } else {
                if (captures.deleteStructureNode(session, templateId, nodeId)) {
                    messages.send(player, "editor.module_deleted", Map.of("type", "node", "id", nodeId));
                    captures.fireRefresh(session.playerId);
                }
                open(player, new Holder(Panel.STRUCTURE, new String[] {templateId}, 0));
            }
            return;
        }
        ModuleNode node = nodeOf(session, templateId, nodeId);
        if (node == null) {
            return;
        }
        if (templateId.isBlank()) {
            List<String> keys = new ArrayList<>(node.params().keySet());
            if (slot < 0 || slot >= keys.size()) {
                return;
            }
            String key = keys.get(slot);
            prompt(player, holder, "node_param", text -> {
                report(player, captures.nodeParam(player, nodeId, key, text));
                reopen(player, holder);
            });
            return;
        }
        List<EditorModules.Field> fields = fieldsOf(node);
        if (slot < 0 || slot >= fields.size()) {
            return;
        }
        EditorModules.Field field = fields.get(slot);
        prompt(player, holder, "node_param", text -> {
            report(player, captures.structureNodeParam(player, templateId, nodeId,
                    List.of(field.path()), text));
            reopen(player, holder);
        });
    }

    private void handlePlace(Player player, CaptureService.Session session, Holder holder, int slot) {
        String templateId = holder.path.length > 0 ? holder.path[0] : "";
        if (slot == SLOT_BACK) {
            session.pendingBlock = null;
            if (templateId.isBlank()) {
                open(player, new Holder(Panel.MAIN, new String[0], 0));
            } else {
                open(player, new Holder(Panel.STRUCTURE, new String[] {templateId}, 0));
            }
            return;
        }
        if (slot < 0 || slot >= NODE_MODULES.size() || session.pendingBlock == null) {
            return;
        }
        String module = NODE_MODULES.get(slot);
        Block block = session.pendingBlock;
        session.pendingBlock = null;
        if (!templateId.isBlank()) {
            String structureNodeId = nextStructureNodeId(session, templateId, module);
            report(player, captures.nodeAddToStructure(player, templateId, module, structureNodeId, "", block));
            open(player, new Holder(Panel.STRUCTURE, new String[] {templateId}, 0));
            return;
        }
        String nodeId = nextNodeId(session, module);
        report(player, captures.nodeAdd(player, module, nodeId, "", block));
        for (ModuleNode node : session.nodes) {
            if (node.nodeId().equals(nodeId)) {
                openNode(player, node);
                return;
            }
        }
        open(player, new Holder(Panel.NODES, new String[0], 0));
    }

    private void handleCreate(Player player, CaptureService.Session session, int slot) {
        if (slot == SLOT_BACK) {
            open(player, new Holder(Panel.MAIN, new String[0], 0));
            return;
        }
        String type = slot == 10 ? TemplateModel.TYPE_ROOM : slot == 11 ? TemplateModel.TYPE_CORRIDOR
                : slot == 12 ? TemplateModel.TYPE_TRANSITION : null;
        if (type == null) {
            return;
        }
        report(player, captures.meta(player, "type", type));
        reopen(player, new Holder(Panel.CREATE, new String[0], 0));
    }

    private void handleStructure(Player player, Holder holder, CaptureService.Session session, int slot) {
        String templateId = holder.path[0];
        TemplateMeta meta = captures.structure(session, templateId);
        if (meta == null) {
            if (slot == SLOT_BACK) {
                open(player, new Holder(Panel.MAIN, new String[0], 0));
            }
            return;
        }
        switch (slot) {
            case 10 -> {
                String next = switch (meta.structureType()) {
                    case TemplateModel.TYPE_ROOM -> TemplateModel.TYPE_CORRIDOR;
                    case TemplateModel.TYPE_CORRIDOR -> TemplateModel.TYPE_TRANSITION;
                    default -> TemplateModel.TYPE_ROOM;
                };
                report(player, captures.retypeStructure(player, templateId, next));
                refreshEditorMarkers(session);
                open(player, new Holder(Panel.MAIN, new String[0], 0));
            }
            case 11 -> promptClose(player, "tags", text -> {
                List<String> tags = new ArrayList<>();
                for (String tag : text.split(",")) {
                    if (!tag.isBlank()) {
                        tags.add(tag.trim());
                    }
                }
                saveStructure(player, templateId, copy(meta, meta.structureType(), tags, meta.floors(),
                        meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                        meta.sealBlock(), meta.nodes()));
            });
            case 12 -> {
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.doorFloors(),
                        meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                        meta.sealBlock(), meta.nodes()));
                reopen(player, holder);
            }
            case 13 -> {
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        meta.rotatable(), !meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                        meta.sealBlock(), meta.nodes()));
                reopen(player, holder);
            }
            case 14 -> {
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        !meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                        meta.sealBlock(), meta.nodes()));
                reopen(player, holder);
            }
            case 15 -> {
                String next = TemplateModel.INTRUSION_REJECT.equals(meta.intrusionPolicy())
                        ? TemplateModel.INTRUSION_ALLOW : TemplateModel.INTRUSION_REJECT;
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        meta.rotatable(), meta.mirrorable(), next, meta.doorPolicy(), meta.sealBlock(),
                        meta.nodes()));
                reopen(player, holder);
            }
            case 16 -> {
                String next = switch (meta.doorPolicy()) {
                    case TemplateModel.POLICY_SEAL -> TemplateModel.POLICY_KEEP;
                    case TemplateModel.POLICY_KEEP -> TemplateModel.POLICY_REJECT;
                    default -> TemplateModel.POLICY_SEAL;
                };
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), next, meta.sealBlock(),
                        meta.nodes()));
                reopen(player, holder);
            }
            case 17 -> promptClose(player, "seal-block", text -> saveStructure(player, templateId,
                    copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                            meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                            text.trim(), meta.nodes())));
            case 18 -> promptClose(player, "weight", text -> saveStructure(player, templateId,
                    copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                            meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                            meta.sealBlock(), meta.nodes(), Math.max(0, parseInt(text, meta.weight())))));
            case SLOT_DELETE -> {
                deleteStructure(player, templateId);
                open(player, new Holder(Panel.MAIN, new String[0], 0));
            }
            case SLOT_BACK -> open(player, new Holder(Panel.MAIN, new String[0], 0));
            default -> {
            }
        }
    }

    private boolean handleNav(Player player, Holder holder, int total, int slot, Panel panel) {
        if (slot == SLOT_PREV && holder.page > 0) {
            open(player, new Holder(panel, holder.path, holder.page - 1));
            return true;
        }
        if (slot == SLOT_NEXT && (holder.page + 1) * 36 < total) {
            open(player, new Holder(panel, holder.path, holder.page + 1));
            return true;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return true;
        }
        return false;
    }


    private TemplateMeta copy(TemplateMeta meta, String structureType, List<String> tags, int floors,
            boolean rotatable, boolean mirrorable, String intrusion, String door, String seal,
            List<ModuleNode> nodes) {
        return copy(meta, structureType, tags, floors, rotatable, mirrorable, intrusion, door, seal,
                nodes, meta.weight());
    }

    private TemplateMeta copy(TemplateMeta meta, String structureType, List<String> tags, int floors,
            boolean rotatable, boolean mirrorable, String intrusion, String door, String seal,
            List<ModuleNode> nodes, int weight) {
        return new TemplateMeta(meta.id(), meta.templateRef(), structureType, tags,
                meta.sizeX(), meta.sizeY(), meta.sizeZ(), floors, meta.anchors(), rotatable,
                mirrorable, intrusion, door, seal, nodes, meta.mask(), meta.entities(), meta.selection(),
                weight);
    }

    private void saveStructure(Player player, String templateId, TemplateMeta meta) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (captures.saveStructure(session, meta)) {
            messages.send(player, "random.gui.structure_saved", Map.of("id", templateId));
        } else {
            messages.send(player, "random.gui.structure_save_failed", Map.of("id", templateId));
        }
        refreshEditorMarkers(session);
        captures.fireRefresh(session.playerId);
    }

    private void deleteStructure(Player player, String templateId) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        captures.deleteStructure(session, templateId);
        messages.send(player, "random.gui.structure_deleted", Map.of("id", templateId));
        refreshEditorMarkers(session);
        captures.fireRefresh(session.playerId);
    }



    private String nextStructureNodeId(CaptureService.Session session, String templateId, String module) {
        java.util.Set<String> used = new java.util.HashSet<>();
        TemplateMeta meta = captures.structure(session, templateId);
        if (meta != null) {
            for (ModuleNode node : meta.nodes()) {
                used.add(node.nodeId());
            }
        }
        int index = 1;
        while (index < 512) {
            String candidate = module + "_" + index;
            if (!used.contains(candidate)) {
                return candidate;
            }
            index++;
        }
        return module + "_" + System.currentTimeMillis();
    }

    private String nextNodeId(CaptureService.Session session, String module) {
        int index = 1;
        while (index < 512) {
            String candidate = module + "_" + index;
            boolean exists = session.nodes.stream().anyMatch(node -> node.nodeId().equals(candidate));
            if (!exists) {
                return candidate;
            }
            index++;
        }
        return module + "_" + System.currentTimeMillis();
    }

    private void reopen(Player player, Holder holder) {
        open(player, holder);
    }

    private void report(Player player, CaptureService.Result result) {
        messages.send(player, result.messageKey(), result.params());
    }

    private void promptClose(Player player, String fieldKey, java.util.function.Consumer<String> handler) {
        String label = messages.message("random.gui.field." + fieldKey, Map.of());
        player.closeInventory();
        messages.send(player, "editor.input_prompt", Map.of("field", label));
        chatInput.await(new ChatInputRequest(plugin, player, 60L, List.of("取消", "cancel"), result -> {
            if (result.submitted()) {
                handler.accept(Texts.trim(MiniMessages.plainText(result.text())));
            }
        }));
    }

    private void prompt(Player player, Holder holder, String fieldKey, java.util.function.Consumer<String> handler) {
        String label = messages.message("random.gui.field." + fieldKey, Map.of());
        messages.send(player, "editor.input_prompt", Map.of("field", label));
        chatInput.await(new ChatInputRequest(plugin, player, 60L, List.of("取消", "cancel"), result -> {
            if (!result.submitted()) {
                reopen(player, holder);
                return;
            }
            handler.accept(Texts.trim(MiniMessages.plainText(result.text())));
        }));
    }

    private String plain(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
    }

    private ItemStack itemRaw(Material material, String display, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        var stackMeta = stack.getItemMeta();
        stackMeta.displayName(MiniMessages.parse(display));
        List<net.kyori.adventure.text.Component> loreLines = new ArrayList<>();
        for (String line : lore) {
            loreLines.add(MiniMessages.parse("<gray>" + plain(line) + "</gray>"));
        }
        stackMeta.lore(loreLines);
        stack.setItemMeta(stackMeta);
        return stack;
    }

    private ItemStack item(Material material, String messageKey, List<String> lore, String... replacements) {
        String text = messages.message(messageKey, argsMap(replacements));
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(MiniMessages.parse(text));
        List<net.kyori.adventure.text.Component> loreLines = new ArrayList<>();
        for (String line : lore) {
            loreLines.add(MiniMessages.parse("<gray>" + line.replace("<", "").replace(">", "") + "</gray>"));
        }
        meta.lore(loreLines);
        stack.setItemMeta(meta);
        return stack;
    }

    private Map<String, String> argsMap(String... replacements) {
        Map<String, String> args = new java.util.LinkedHashMap<>();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            args.put(replacements[index], replacements[index + 1]);
        }
        return args;
    }

    private String coordLine(int[] pos) {
        return pos == null ? "-" : pos[0] + "," + pos[1] + "," + pos[2];
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
