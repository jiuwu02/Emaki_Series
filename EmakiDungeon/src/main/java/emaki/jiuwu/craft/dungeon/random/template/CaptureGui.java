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
import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;

public final class CaptureGui implements Listener {

    static final List<String> NODE_MODULES = List.of(
            "spawn_points", "respawn_points", "check_points", "exit_points", "block_points",
            "reward_points", "option_points", "tip_points", "teleport_zones", "boundary_zones",
            "effect_zones", "mechanism_zones", "room_timer_zones", "item_points", "fx_points",
            "temp_items", "kill_targets");

    private enum Panel {
        MAIN, META, ANCHORS, NODES, NODE, PLACE, STRUCTURE, CREATE
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
    private static final int SLOT_DISCARD = 40;
    private static final int SLOT_DELETE = 48;

    private final Plugin plugin;
    private final CaptureService captures;
    private final MessageService messages;
    private final ChatInputService chatInput;
    private java.util.function.Supplier<RandomConfig> configSupplier;

    public CaptureGui(Plugin plugin, CaptureService captures, MessageService messages, ChatInputService chatInput) {
        this.plugin = plugin;
        this.captures = captures;
        this.messages = messages;
        this.chatInput = chatInput;
    }

    public void bindConfigSupplier(java.util.function.Supplier<RandomConfig> configSupplier) {
        this.configSupplier = configSupplier;
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
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.pendingBlock = block;
        open(player, new Holder(Panel.PLACE, new String[0], 0));
    }

    private void open(Player player, Holder holder) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        String title = messages.message("random.gui.title", Map.of());
        Inventory inventory = plugin.getServer().createInventory(holder, 54, MiniMessages.parse(title));
        render(inventory, session, holder);
        player.openInventory(inventory);
    }

    private void render(Inventory inventory, CaptureService.Session session, Holder holder) {
        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, null);
        }
        switch (holder.panel) {
            case MAIN -> renderMain(inventory, session);
            case META -> renderMeta(inventory, session);
            case ANCHORS -> renderAnchors(inventory, session, holder);
            case NODES -> renderNodes(inventory, session, holder);
            case NODE -> renderNode(inventory, session, holder);
            case PLACE -> renderPlace(inventory);
            case STRUCTURE -> renderStructure(inventory, session, holder);
            case CREATE -> renderCreate(inventory);
        }
    }

    private void renderMain(Inventory inventory, CaptureService.Session session) {
        inventory.setItem(SLOT_POS1, item(Material.LIME_TERRACOTTA, "random.gui.pos1",
                List.of(coordLine(session.pos1))));
        inventory.setItem(SLOT_POS2, item(Material.LIME_TERRACOTTA, "random.gui.pos2",
                List.of(coordLine(session.pos2))));
        inventory.setItem(SLOT_META, item(Material.BOOK, "random.gui.meta", List.of()));
        inventory.setItem(SLOT_ANCHORS, item(Material.IRON_DOOR, "random.gui.anchors",
                List.of(String.valueOf(session.anchors.size()))));
        inventory.setItem(SLOT_NODES, item(Material.CHEST, "random.gui.nodes",
                List.of(String.valueOf(session.nodes.size()))));
        inventory.setItem(SLOT_SAVE, item(Material.LIME_CONCRETE, "random.gui.create_region", List.of()));
        inventory.setItem(SLOT_DISCARD, item(Material.ORANGE_CONCRETE, "random.gui.discard", List.of()));
        inventory.setItem(SLOT_STATUS, item(Material.ENDER_EYE, "random.gui.status",
                List.of(session.structureType, String.join(",", session.tags),
                        String.valueOf(session.pos1 != null && session.pos2 != null))));
        inventory.setItem(SLOT_CLOSE, item(Material.BARRIER, "random.gui.close", List.of()));
    }

    private void renderMeta(Inventory inventory, CaptureService.Session session) {
        inventory.setItem(10, item(Material.PAPER, "random.gui.meta_type", List.of(session.structureType)));
        inventory.setItem(11, item(Material.NAME_TAG, "random.gui.meta_tags",
                List.of(String.join(",", session.tags))));
        inventory.setItem(12, item(Material.LADDER, "random.gui.meta_floors",
                List.of(String.valueOf(session.floors))));
        inventory.setItem(13, item(Material.GLASS_PANE, "random.gui.meta_mirrorable",
                List.of(String.valueOf(session.mirrorable))));
        inventory.setItem(14, item(Material.COMPASS, "random.gui.meta_rotations",
                List.of(session.rotations.toString())));
        inventory.setItem(15, item(Material.SHIELD, "random.gui.meta_intrusion", List.of(session.intrusionPolicy)));
        inventory.setItem(16, item(Material.OAK_DOOR, "random.gui.meta_door", List.of(session.doorPolicy)));
        inventory.setItem(17, item(Material.BRICKS, "random.gui.meta_seal", List.of(session.sealBlock)));
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderAnchors(Inventory inventory, CaptureService.Session session, Holder holder) {
        int start = holder.page * 36;
        for (int index = 0; index < 36 && start + index < session.anchors.size(); index++) {
            DoorAnchor anchor = session.anchors.get(start + index);
            inventory.setItem(index, itemRaw(Material.IRON_DOOR,
                    "<white>" + plain(anchor.id()) + "</white>",
                    List.of(anchor.facing(), anchor.width() + "x" + anchor.height())));
        }
        renderNav(inventory, holder, session.anchors.size(), Material.LIME_DYE, "random.gui.anchor_add");
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
        Map<String, Object> params = Map.of();
        if (templateId.isBlank()) {
            for (ModuleNode node : session.nodes) {
                if (node.nodeId().equals(nodeId)) {
                    params = node.params();
                    inventory.setItem(SLOT_INFO, itemRaw(Material.BOOK,
                            "<white>" + plain(node.moduleType()) + "</white> <gray>"
                                    + plain(node.nodeId()) + "</gray>", List.of()));
                    break;
                }
            }
        } else {
            TemplateMeta meta = captures.structure(session, templateId);
            if (meta != null) {
                for (ModuleNode node : meta.nodes()) {
                    if (node.nodeId().equals(nodeId)) {
                        params = node.params();
                        inventory.setItem(SLOT_INFO, itemRaw(Material.BOOK,
                                "<white>" + plain(node.moduleType()) + "</white> <gray>"
                                        + plain(node.nodeId()) + "</gray>", List.of()));
                        break;
                    }
                }
            }
        }
        int slot = 0;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (slot >= 40) {
                break;
            }
            inventory.setItem(slot++, itemRaw(Material.PAPER,
                    "<white>" + plain(entry.getKey()) + "</white> <gray>"
                            + plain(String.valueOf(entry.getValue())) + "</gray>", List.of()));
        }
        inventory.setItem(SLOT_DELETE, item(Material.RED_CONCRETE, "random.gui.node_delete", List.of()));
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderPlace(Inventory inventory) {
        for (int index = 0; index < NODE_MODULES.size() && index < 45; index++) {
            inventory.setItem(index, itemRaw(Material.LIME_DYE,
                    "<white>" + plain(NODE_MODULES.get(index)) + "</white>", List.of()));
        }
        inventory.setItem(SLOT_BACK, item(Material.OAK_DOOR, "random.gui.back", List.of()));
    }

    private void renderCreate(Inventory inventory) {
        inventory.setItem(10, item(Material.LIGHT_BLUE_CONCRETE, "random.gui.create_room", List.of()));
        inventory.setItem(11, item(Material.ORANGE_CONCRETE, "random.gui.create_corridor", List.of()));
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
                List.of(String.valueOf(meta.floors()))));
        inventory.setItem(13, item(Material.GLASS_PANE, "random.gui.meta_mirrorable",
                List.of(String.valueOf(meta.mirrorable()))));
        inventory.setItem(14, item(Material.COMPASS, "random.gui.meta_rotations",
                List.of(meta.rotations().toString())));
        inventory.setItem(15, item(Material.SHIELD, "random.gui.meta_intrusion", List.of(meta.intrusionPolicy())));
        inventory.setItem(16, item(Material.OAK_DOOR, "random.gui.meta_door", List.of(meta.doorPolicy())));
        inventory.setItem(17, item(Material.BRICKS, "random.gui.meta_seal", List.of(meta.sealBlock())));
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
            case NODES -> handleNodes(player, holder, session, slot);
            case NODE -> handleNode(player, holder, session, slot);
            case PLACE -> handlePlace(player, session, slot);
            case STRUCTURE -> handleStructure(player, holder, session, slot);
            case CREATE -> handleCreate(player, slot);
        }
    }

    private void handleMain(Player player, CaptureService.Session session, int slot) {
        switch (slot) {
            case SLOT_POS1 -> {
                report(player, captures.pos(player, 1));
                reopen(player, new Holder(Panel.MAIN, new String[0], 0));
            }
            case SLOT_POS2 -> {
                report(player, captures.pos(player, 2));
                reopen(player, new Holder(Panel.MAIN, new String[0], 0));
            }
            case SLOT_META -> open(player, new Holder(Panel.META, new String[0], 0));
            case SLOT_ANCHORS -> open(player, new Holder(Panel.ANCHORS, new String[0], 0));
            case SLOT_NODES -> open(player, new Holder(Panel.NODES, new String[0], 0));
            case SLOT_SAVE -> open(player, new Holder(Panel.CREATE, new String[0], 0));
            case SLOT_DISCARD -> {
                captures.discard(player);
                player.closeInventory();
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleMeta(Player player, Holder holder, CaptureService.Session session, int slot) {
        switch (slot) {
            case 10 -> {
                String next = TemplateModel.TYPE_ROOM.equals(session.structureType)
                        ? TemplateModel.TYPE_CORRIDOR : TemplateModel.TYPE_ROOM;
                report(player, captures.meta(player, "type", next));
                reopen(player, holder);
            }
            case 11 -> prompt(player, holder, "tags", text -> {
                report(player, captures.meta(player, "tags", text));
                reopen(player, holder);
            });
            case 12 -> prompt(player, holder, "floors", text -> {
                report(player, captures.meta(player, "floors", text));
                reopen(player, holder);
            });
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
            case 17 -> prompt(player, holder, "seal-block", text -> {
                report(player, captures.meta(player, "seal-block", text));
                reopen(player, holder);
            });
            case SLOT_BACK -> open(player, new Holder(Panel.MAIN, new String[0], 0));
            default -> {
            }
        }
    }

    private void handleAnchors(Player player, Holder holder, CaptureService.Session session, int slot) {
        if (handleNav(player, holder, session.anchors.size(), slot, Panel.ANCHORS)) {
            return;
        }
        if (slot == SLOT_ACTION) {
            prompt(player, holder, "anchor_add", text -> {
                String[] parts = text.trim().split("\\s+");
                if (parts.length < 4) {
                    messages.send(player, "random.template.usage", Map.of());
                    reopen(player, holder);
                    return;
                }
                try {
                    int width = Integer.parseInt(parts[2]);
                    int height = Integer.parseInt(parts[3]);
                    report(player, captures.anchorAdd(player, parts[0], parts[1], width, height));
                } catch (NumberFormatException exception) {
                    messages.send(player, "random.template.usage", Map.of());
                }
                reopen(player, holder);
            });
            return;
        }
        int start = holder.page * 36;
        if (slot >= 0 && slot < 36 && start + slot < session.anchors.size()) {
            String id = session.anchors.get(start + slot).id();
            report(player, captures.anchorRemove(player, id));
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
                open(player, new Holder(Panel.NODES, new String[0], 0));
            } else {
                deleteStructureNode(player, templateId, nodeId);
                open(player, new Holder(Panel.STRUCTURE, new String[] {templateId}, 0));
            }
            return;
        }
        List<String> keys = paramKeys(session, templateId, nodeId);
        if (slot >= 0 && slot < keys.size()) {
            String key = keys.get(slot);
            prompt(player, holder, "node_param", text -> {
                if (templateId.isBlank()) {
                    report(player, captures.nodeParam(player, nodeId, key, text));
                } else {
                    setStructureNodeParam(player, templateId, nodeId, key, text);
                }
                reopen(player, holder);
            });
        }
    }

    private void handlePlace(Player player, CaptureService.Session session, int slot) {
        if (slot == SLOT_BACK) {
            session.pendingBlock = null;
            open(player, new Holder(Panel.MAIN, new String[0], 0));
            return;
        }
        if (slot < 0 || slot >= NODE_MODULES.size() || session.pendingBlock == null) {
            return;
        }
        String module = NODE_MODULES.get(slot);
        String nodeId = nextNodeId(session, module);
        Block block = session.pendingBlock;
        session.pendingBlock = null;
        report(player, captures.nodeAdd(player, module, nodeId, "", block));
        for (ModuleNode node : session.nodes) {
            if (node.nodeId().equals(nodeId)) {
                openNode(player, node);
                return;
            }
        }
        open(player, new Holder(Panel.NODES, new String[0], 0));
    }

    private void handleCreate(Player player, int slot) {
        if (slot == SLOT_BACK) {
            open(player, new Holder(Panel.MAIN, new String[0], 0));
            return;
        }
        String type = slot == 10 ? TemplateModel.TYPE_ROOM : slot == 11 ? TemplateModel.TYPE_CORRIDOR : null;
        if (type == null) {
            return;
        }
        RandomConfig config = configSupplier == null ? null : configSupplier.get();
        report(player, captures.createRegion(player, type, config));
        open(player, new Holder(Panel.MAIN, new String[0], 0));
    }

    private void handleStructure(Player player, Holder holder, CaptureService.Session session, int slot) {
        String templateId = holder.path[0];
        TemplateMeta meta = captures.structure(session, templateId);
        if (meta == null) {
            if (slot == SLOT_BACK) {
                player.closeInventory();
            }
            return;
        }
        switch (slot) {
            case 10 -> {
                String next = TemplateModel.TYPE_ROOM.equals(meta.structureType())
                        ? TemplateModel.TYPE_CORRIDOR : TemplateModel.TYPE_ROOM;
                saveStructure(player, templateId, copy(meta, next, meta.tags(), meta.floors(),
                        meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), meta.sealBlock(),
                        meta.rotations(), meta.nodes()));
                reopen(player, holder);
            }
            case 11 -> prompt(player, holder, "tags", text -> {
                List<String> tags = new ArrayList<>();
                for (String tag : text.split(",")) {
                    if (!tag.isBlank()) {
                        tags.add(tag.trim());
                    }
                }
                saveStructure(player, templateId, copy(meta, meta.structureType(), tags, meta.floors(),
                        meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), meta.sealBlock(),
                        meta.rotations(), meta.nodes()));
                reopen(player, holder);
            });
            case 12 -> prompt(player, holder, "floors", text -> {
                int floors = Math.max(1, parseInt(text, meta.floors()));
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), floors,
                        meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), meta.sealBlock(),
                        meta.rotations(), meta.nodes()));
                reopen(player, holder);
            });
            case 13 -> {
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        !meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), meta.sealBlock(),
                        meta.rotations(), meta.nodes()));
                reopen(player, holder);
            }
            case 14 -> prompt(player, holder, "rotations", text -> {
                List<Integer> rotations = new ArrayList<>();
                for (String part : text.split(",")) {
                    if (!part.isBlank()) {
                        int rotation = Integer.parseInt(part.trim());
                        if (rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270) {
                            rotations.add(rotation);
                        }
                    }
                }
                if (rotations.isEmpty()) {
                    rotations = new ArrayList<>(meta.rotations());
                }
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), meta.sealBlock(),
                        rotations, meta.nodes()));
                reopen(player, holder);
            });
            case 15 -> {
                String next = TemplateModel.INTRUSION_REJECT.equals(meta.intrusionPolicy())
                        ? TemplateModel.INTRUSION_ALLOW : TemplateModel.INTRUSION_REJECT;
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        meta.mirrorable(), next, meta.doorPolicy(), meta.sealBlock(),
                        meta.rotations(), meta.nodes()));
                reopen(player, holder);
            }
            case 16 -> {
                String next = switch (meta.doorPolicy()) {
                    case TemplateModel.POLICY_SEAL -> TemplateModel.POLICY_KEEP;
                    case TemplateModel.POLICY_KEEP -> TemplateModel.POLICY_REJECT;
                    default -> TemplateModel.POLICY_SEAL;
                };
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        meta.mirrorable(), meta.intrusionPolicy(), next, meta.sealBlock(),
                        meta.rotations(), meta.nodes()));
                reopen(player, holder);
            }
            case 17 -> prompt(player, holder, "seal-block", text -> {
                saveStructure(player, templateId, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                        meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), text.trim(),
                        meta.rotations(), meta.nodes()));
                reopen(player, holder);
            });
            case SLOT_DELETE -> {
                deleteStructure(player, templateId);
                player.closeInventory();
            }
            case SLOT_BACK -> {
                session.pendingBlock = null;
                player.closeInventory();
            }
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
        return slot == SLOT_CLOSE;
    }

    private List<String> paramKeys(CaptureService.Session session, String templateId, String nodeId) {
        if (templateId.isBlank()) {
            for (ModuleNode node : session.nodes) {
                if (node.nodeId().equals(nodeId)) {
                    return new ArrayList<>(node.params().keySet());
                }
            }
            return List.of();
        }
        TemplateMeta meta = captures.structure(session, templateId);
        if (meta != null) {
            for (ModuleNode node : meta.nodes()) {
                if (node.nodeId().equals(nodeId)) {
                    return new ArrayList<>(node.params().keySet());
                }
            }
        }
        return List.of();
    }

    private TemplateMeta copy(TemplateMeta meta, String structureType, List<String> tags, int floors,
            boolean mirrorable, String intrusion, String door, String seal, List<Integer> rotations,
            List<ModuleNode> nodes) {
        return new TemplateMeta(meta.id(), meta.templateRef(), structureType, tags,
                meta.sizeX(), meta.sizeY(), meta.sizeZ(), floors, meta.anchors(), rotations,
                mirrorable, intrusion, door, seal, nodes, meta.mask(), meta.entities(), meta.selection());
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
        captures.fireRefresh(session.playerId);
    }

    private void deleteStructure(Player player, String templateId) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        captures.deleteStructure(session, templateId);
        messages.send(player, "random.gui.structure_deleted", Map.of("id", templateId));
        captures.fireRefresh(session.playerId);
    }

    private void deleteStructureNode(Player player, String templateId, String nodeId) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        TemplateMeta meta = captures.structure(session, templateId);
        if (meta == null) {
            return;
        }
        List<ModuleNode> nodes = new ArrayList<>();
        for (ModuleNode node : meta.nodes()) {
            if (!node.nodeId().equals(nodeId)) {
                nodes.add(node);
            }
        }
        if (captures.saveStructure(session, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), meta.sealBlock(),
                meta.rotations(), nodes))) {
            messages.send(player, "editor.module_deleted", Map.of("type", "node", "id", nodeId));
        }
        captures.fireRefresh(session.playerId);
    }

    private void setStructureNodeParam(Player player, String templateId, String nodeId, String key, String value) {
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        TemplateMeta meta = captures.structure(session, templateId);
        if (meta == null) {
            return;
        }
        List<ModuleNode> nodes = new ArrayList<>();
        for (ModuleNode node : meta.nodes()) {
            if (node.nodeId().equals(nodeId)) {
                Map<String, Object> params = new java.util.LinkedHashMap<>(node.params());
                params.put(key, value);
                nodes.add(new ModuleNode(node.moduleType(), node.nodeId(), node.anchorId(),
                        node.dx(), node.dy(), node.dz(), node.yaw(), params));
            } else {
                nodes.add(node);
            }
        }
        if (captures.saveStructure(session, copy(meta, meta.structureType(), meta.tags(), meta.floors(),
                meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(), meta.sealBlock(),
                meta.rotations(), nodes))) {
            messages.send(player, "random.template.node_param_set", Map.of("id", nodeId, "key", key));
        }
        captures.fireRefresh(session.playerId);
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
