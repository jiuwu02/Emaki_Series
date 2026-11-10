package emaki.jiuwu.craft.dungeon.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.editor.EditorDocument;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.flow.FlowPoint;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class EditorGui implements Listener {

    static final int CONTENT_SLOTS = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_ACTION = 46;
    static final int SLOT_NEXT = 47;
    static final int SLOT_BACK = 49;
    static final int SLOT_INFO = 50;
    static final int SLOT_CLOSE = 53;
    private static final int SLOT_HELP = 14;
    private static final int SLOT_EXITS = 15;
    private static final int SLOT_POS1 = 20;
    private static final int SLOT_POS2 = 21;
    private static final int SLOT_STATUS = 26;
    private static final int SLOT_SAVE = 45;
    private static final int SLOT_DISCARD = 47;
    private static final int SLOT_TEST = 49;
    private static final int SLOT_EXIT = 51;
    private static final int SLOT_META = 10;
    private static final int SLOT_WAVES = 11;
    private static final int SLOT_GATES = 12;
    private static final int SLOT_BRANCHES = 13;
    private static final int SLOT_MODULES = 16;
    private static final int MODULE_INFO_SLOT = 44;
    private static final int OPTION_PAGE_SIZE = 27;
    static final int LIST_PAGE_SIZE = 45;

    enum Panel {
        MAIN, META, WAVES, SPAWN, WAVE, GATES, GATE, BRANCHES, OPTION, CASE, EXITS,
        MODULES, MODULE_FIELDS
    }

    static final class EditorHolder implements InventoryHolder {

        final UUID playerId;
        final Panel panel;
        final String[] path;
        final int page;
        private boolean discardArmed;
        Inventory inventory;

        EditorHolder(UUID playerId, Panel panel, String[] path, int page) {
            this.playerId = playerId;
            this.panel = panel;
            this.path = path;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    record MetaField(String key, Material material, String[] path, boolean numeric) {
    }

    static final List<MetaField> META_FIELDS = List.of(
            new MetaField("display_name", Material.NAME_TAG, new String[] { "display-name" }, false),
            new MetaField("description", Material.PAPER, new String[] { "description" }, false),
            new MetaField("difficulty_group", Material.EMERALD, new String[] { "difficulty-group" }, false),
            new MetaField("prepare_window", Material.CLOCK, new String[] { "enter", "prepare-window-seconds" }, true),
            new MetaField("pool_max", Material.HOPPER, new String[] { "pool", "max-worlds" }, true),
            new MetaField("leaderboard_max", Material.GOLDEN_HELMET,
                    new String[] { "leaderboard", "max-entries" }, true)
    );

    private final Plugin plugin;
    final EditorService service;
    final MessageService messages;
    private final ChatInputService chatInput;

    public EditorGui(Plugin plugin,
            EditorService service,
            MessageService messages,
            ChatInputService chatInput) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
        this.chatInput = chatInput;
    }

    public void openMain(Player player) { new EditorGuiDelegate(this).openMain(player); }

    public void closeIfEditor(Player player) { new EditorGuiDelegate(this).closeIfEditor(player); }

    void open(Player player, Panel panel, String[] path, int page) { new EditorGuiDelegate(this).open(player, panel, path, page); }

    void reopen(Player player, EditorHolder holder) { new EditorGuiDelegate(this).reopen(player, holder); }

    Inventory render(Player player, EditorHolder holder) {
        EditorSession session = service.sessionOf(holder.playerId);
        String title = messages.message("editor.gui.title", Map.of(
                "dungeon", session == null ? "-" : session.dungeonId()));
        Inventory inventory = Bukkit.createInventory(holder, 54, MiniMessages.parse(title));
        holder.inventory = inventory;
        if (session == null) {
            return inventory;
        }
        switch (holder.panel) {
            case MAIN -> renderMain(inventory, session, holder);
            case META -> renderMeta(inventory, session, holder);
            case WAVES -> renderSpawnList(inventory, session, holder);
            case SPAWN -> renderSpawnWaves(inventory, session, holder);
            case WAVE -> renderWave(inventory, session, holder);
            case GATES -> renderGateList(inventory, session, holder);
            case GATE -> renderGate(inventory, session, holder);
            case BRANCHES -> renderOptionList(inventory, session, holder);
            case OPTION -> renderOption(inventory, session, holder);
            case CASE -> renderCase(inventory, session, holder);
            case EXITS -> renderExitList(inventory, session, holder);
            case MODULES -> renderModules(inventory, session, holder);
            case MODULE_FIELDS -> renderModuleFields(inventory, session, holder);
            default -> {
            }
        }
        fill(inventory);
        return inventory;
    }

    void renderMain(Inventory inventory, EditorSession session, EditorHolder holder) {
        inventory.setItem(SLOT_META, navItem(Material.BOOK, "editor.gui.meta"));
        inventory.setItem(SLOT_WAVES, navItem(Material.REDSTONE, "editor.gui.waves"));
        inventory.setItem(SLOT_GATES, navItem(Material.IRON_DOOR, "editor.gui.gates"));
        inventory.setItem(SLOT_BRANCHES, navItem(Material.SPRUCE_SAPLING, "editor.gui.branches"));
        inventory.setItem(SLOT_HELP, navItem(Material.COMPASS, "editor.gui.help"));
        inventory.setItem(SLOT_EXITS, navItem(Material.RED_BED, "editor.gui.exits"));
        inventory.setItem(SLOT_MODULES, navItem(Material.CHEST, "editor.gui.modules"));
        inventory.setItem(SLOT_POS1, navItem(Material.LIME_TERRACOTTA, "editor.gui.pos1",
                "coord", pointText(session.pos1())));
        inventory.setItem(SLOT_POS2, navItem(Material.LIME_TERRACOTTA, "editor.gui.pos2",
                "coord", pointText(session.pos2())));
        inventory.setItem(SLOT_STATUS, navItem(Material.ENDER_EYE, "editor.gui.status",
                "marks", String.valueOf(session.stagedMarks().size()),
                "state", messages.message(session.dirty() ? "editor.gui.state_dirty"
                        : "editor.gui.state_clean", Map.of()),
                "generation", String.valueOf(service.states().generationOf(session.dungeonId()))));
        inventory.setItem(SLOT_SAVE, navItem(Material.LIME_CONCRETE, "editor.gui.save"));
        inventory.setItem(SLOT_DISCARD, navItem(holder.discardArmed ? Material.ORANGE_CONCRETE
                : Material.RED_CONCRETE, holder.discardArmed ? "editor.gui.discard_confirm"
                : "editor.gui.discard"));
        inventory.setItem(SLOT_TEST, navItem(Material.PISTON, "editor.gui.test"));
        inventory.setItem(SLOT_EXIT, navItem(Material.OAK_DOOR, "editor.gui.exit"));
        inventory.setItem(SLOT_CLOSE, navItem(Material.BARRIER, "editor.gui.close"));
    }

    void renderMeta(Inventory inventory, EditorSession session, EditorHolder holder) { new EditorGuiDelegate(this).renderMeta(inventory, session, holder); }

    void renderSpawnList(Inventory inventory, EditorSession session, EditorHolder holder) {
        List<String> ids = mapKeys(session.flowDoc().view(), "spawn_points");
        int[] range = pageRange(holder.page, ids.size(), LIST_PAGE_SIZE);
        for (int index = range[0]; index < range[1]; index++) {
            String id = ids.get(index);
            int waves = sequenceSize(session.flowDoc().view(), "spawn_points", id, "waves");
            inventory.setItem(index - range[0], item(Material.SPAWNER, id,
                    List.of(valueLine(messages.message("editor.gui.wave_count",
                            Map.of("count", String.valueOf(waves)))))));
        }
        int pages = pageCount(ids.size(), LIST_PAGE_SIZE);
        renderNav(inventory, holder, pages, Material.COMPASS);
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    void renderSpawnWaves(Inventory inventory, EditorSession session, EditorHolder holder) {
        String spawnId = holder.path[0];
        YamlTextDocument view = session.flowDoc().view();
        List<Object> waves = sequence(view, "spawn_points", spawnId, "waves");
        int[] range = pageRange(holder.page, waves.size(), LIST_PAGE_SIZE);
        for (int index = range[0]; index < range[1]; index++) {
            Object wave = waves.get(index);
            String interval = mapText(wave, "interval", "-");
            int mobs = wave instanceof Map<?, ?> map && map.get("mobs") instanceof List<?> list
                    ? list.size() : 0;
            inventory.setItem(index - range[0], item(Material.REDSTONE, "editor.gui.wave_item",
                    List.of(valueLine(interval),
                            valueLine(messages.message("editor.gui.mob_count",
                                    Map.of("count", String.valueOf(mobs)))))));
        }
        int pages = pageCount(waves.size(), LIST_PAGE_SIZE);
        renderNav(inventory, holder, pages, null);
        inventory.setItem(SLOT_ACTION, navItem(Material.LIME_DYE, "editor.gui.wave_add"));
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_waves"));
    }

    void renderWave(Inventory inventory, EditorSession session, EditorHolder holder) { new EditorGuiDelegate(this).renderWave(inventory, session, holder); }

    void renderGateList(Inventory inventory, EditorSession session, EditorHolder holder) {
        List<String> ids = mapKeys(session.flowDoc().view(), "gates");
        int[] range = pageRange(holder.page, ids.size(), LIST_PAGE_SIZE);
        for (int index = range[0]; index < range[1]; index++) {
            String id = ids.get(index);
            int regions = sequenceSize(session.flowDoc().view(), "gates", id, "door_regions");
            inventory.setItem(index - range[0], item(Material.IRON_TRAPDOOR, id,
                    List.of(valueLine(messages.message("editor.gui.region_count",
                            Map.of("count", String.valueOf(regions)))))));
        }
        int pages = pageCount(ids.size(), LIST_PAGE_SIZE);
        renderNav(inventory, holder, pages, Material.COMPASS);
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    void renderGate(Inventory inventory, EditorSession session, EditorHolder holder) {
        String gateId = holder.path[0];
        YamlTextDocument view = session.flowDoc().view();
        String logic = textOf(view.value("gates", gateId, "logic"), "AND");
        String openMode = textOf(view.value("gates", gateId, "open_mode"), "blocks");
        int regions = sequenceSize(view, "gates", gateId, "door_regions");
        int conditions = sequenceSize(view, "gates", gateId, "conditions");
        inventory.setItem(10, navItem("AND".equalsIgnoreCase(logic) ? Material.LIME_DYE
                : Material.ORANGE_DYE, "editor.gui.field.logic"));
        inventory.setItem(12, item(Material.REPEATER, "editor.gui.field.open_mode",
                List.of(valueLine(openMode))));
        inventory.setItem(14, item(Material.IRON_TRAPDOOR, "editor.gui.field.door_regions",
                List.of(valueLine(messages.message("editor.gui.region_count",
                        Map.of("count", String.valueOf(regions)))))));
        inventory.setItem(16, item(Material.PAPER, "editor.gui.field.conditions",
                List.of(valueLine(String.valueOf(conditions)))));
        inventory.setItem(18, navItem(Material.BARRIER, "editor.gui.region_remove"));
        renderNav(inventory, holder, 1, null);
        inventory.setItem(SLOT_ACTION, navItem(Material.LIME_DYE, "editor.gui.region_add"));
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_gates"));
    }

    void renderExitList(Inventory inventory, EditorSession session, EditorHolder holder) {
        YamlTextDocument view = session.flowDoc().view();
        List<String> ids = mapKeys(view, "exit_points");
        int[] range = pageRange(holder.page, ids.size(), LIST_PAGE_SIZE);
        for (int index = range[0]; index < range[1]; index++) {
            String id = ids.get(index);
            Object confirm = view.value("exit_points", id, "need_confirm");
            String mode = Boolean.FALSE.equals(confirm)
                    ? messages.message("editor.gui.exit_mode_direct", Map.of())
                    : messages.message("editor.gui.exit_mode_confirm", Map.of());
            inventory.setItem(index - range[0], item(Material.RED_BED, "editor.gui.exit_item",
                    List.of(valueLine(id), valueLine(mode))));
        }
        int pages = pageCount(ids.size(), LIST_PAGE_SIZE);
        renderNav(inventory, holder, pages, Material.COMPASS);
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    void renderOptionList(Inventory inventory, EditorSession session, EditorHolder holder) { new EditorGuiDelegate(this).renderOptionList(inventory, session, holder); }

    void renderOption(Inventory inventory, EditorSession session, EditorHolder holder) {
        String optionId = holder.path[0];
        YamlTextDocument view = session.flowDoc().view();
        List<String> caseKeys = mapKeys(view, "options", optionId, "cases");
        int[] range = pageRange(holder.page, caseKeys.size(), OPTION_PAGE_SIZE);
        for (int index = range[0]; index < range[1]; index++) {
            String caseKey = caseKeys.get(index);
            String label = textOf(view.value("options", optionId, "cases", caseKey, "label"), caseKey);
            int seals = sequenceSize(view, "options", optionId, "cases", caseKey, "seal_regions");
            inventory.setItem(index - range[0], item(Material.PAPER, "editor.gui.case_item",
                    List.of(valueLine(caseKey + " · " + label),
                            valueLine(messages.message("editor.gui.region_count",
                                    Map.of("count", String.valueOf(seals)))))));
        }
        String mode = textOf(view.value("options", optionId, "select_mode"), "single");
        inventory.setItem(27, navItem("multi".equalsIgnoreCase(mode) ? Material.ORANGE_DYE
                : Material.LIME_DYE, "multi".equalsIgnoreCase(mode)
                        ? "editor.gui.field.select_mode_multi" : "editor.gui.field.select_mode_single"));
        Object timeLimit = view.value("options", optionId, "time_limit_seconds");
        inventory.setItem(28, item(Material.CLOCK, "editor.gui.field.time_limit",
                List.of(valueLine(timeLimit == null ? "0" : String.valueOf(timeLimit)))));
        Object minSelect = view.value("options", optionId, "min_select");
        inventory.setItem(29, item(Material.ARROW, "editor.gui.field.min_select",
                List.of(valueLine(minSelect == null ? "1" : String.valueOf(minSelect)))));
        Object maxSelect = view.value("options", optionId, "max_select");
        inventory.setItem(30, item(Material.ARROW, "editor.gui.field.max_select",
                List.of(valueLine(maxSelect == null ? "1" : String.valueOf(maxSelect)))));
        inventory.setItem(31, item(Material.ENDER_PEARL, "editor.gui.field.safe_point",
                List.of(valueLine(textOf(view.value("options", optionId, "safe-point"), "-")))));
        int endingRules = mapSize(view.value("options", optionId, "ending_map"));
        inventory.setItem(32, item(Material.WRITABLE_BOOK, "editor.gui.field.ending_map",
                List.of(valueLine(String.valueOf(endingRules)))));
        int pages = pageCount(caseKeys.size(), OPTION_PAGE_SIZE);
        renderNav(inventory, holder, pages, null);
        inventory.setItem(SLOT_ACTION, navItem(Material.PAPER, "editor.gui.case_add"));
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_branches"));
    }

    void renderCase(Inventory inventory, EditorSession session, EditorHolder holder) {
        String optionId = holder.path[0];
        String caseKey = holder.path[1];
        YamlTextDocument view = session.flowDoc().view();
        String base = "options." + optionId + ".cases." + caseKey;
        String label = textOf(view.value("options", optionId, "cases", caseKey, "label"), caseKey);
        String caseValue = textOf(view.value("options", optionId, "cases", caseKey, "case"), caseKey);
        String ending = textOf(view.value("options", optionId, "cases", caseKey, "ending"), "-");
        int seals = sequenceSize(view, "options", optionId, "cases", caseKey, "seal_regions");
        inventory.setItem(10, item(Material.NAME_TAG, "editor.gui.field.case_label",
                List.of(valueLine(label))));
        inventory.setItem(12, item(Material.PAPER, "editor.gui.field.case_value",
                List.of(valueLine(caseValue))));
        inventory.setItem(14, item(Material.ENDER_EYE, "editor.gui.field.case_ending",
                List.of(valueLine(ending))));
        inventory.setItem(16, item(Material.BARRIER, "editor.gui.field.seal_regions",
                List.of(valueLine(messages.message("editor.gui.region_count",
                        Map.of("count", String.valueOf(seals)))))));
        inventory.setItem(18, navItem(Material.BARRIER, "editor.gui.region_remove"));
        renderNav(inventory, holder, 1, null);
        inventory.setItem(SLOT_ACTION, navItem(Material.LIME_DYE, "editor.gui.region_add"));
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_option"));
    }

    void renderModules(Inventory inventory, EditorSession session, EditorHolder holder) { new EditorGuiDelegate(this).renderModules(inventory, session, holder); }

    void renderModuleFields(Inventory inventory, EditorSession session, EditorHolder holder) {
        EditorModules.ModuleSpec spec = EditorModules.of(holder.path[0]);
        if (spec == null) {
            return;
        }
        YamlTextDocument view = session.flowDoc().view();
        List<String> nodeIds = moduleNodeIds(view, spec);
        int pages = Math.max(1, nodeIds.size());
        if (nodeIds.isEmpty()) {
            inventory.setItem(MODULE_INFO_SLOT, navItem(Material.BARRIER,
                    "editor.gui.module_no_nodes", "type", spec.section()));
        } else {
            int nodeIndex = Math.max(0, Math.min(holder.page, nodeIds.size() - 1));
            String nodeId = nodeIds.get(nodeIndex);
            List<EditorModules.Field> fields = spec.fields();
            for (int index = 0; index < fields.size(); index++) {
                EditorModules.Field field = fields.get(index);
                Object current = view.value(moduleFieldPath(spec, nodeId, field));
                inventory.setItem(index, item(materialOf(field.kind()), fieldKey(field.key()),
                        List.of(valueLine(textOf(current, "-")))));
            }
            inventory.setItem(MODULE_INFO_SLOT, navItem(Material.BOOK, "editor.gui.module_node",
                    "id", nodeId.isEmpty() ? spec.section() : nodeId,
                    "index", String.valueOf(nodeIndex + 1),
                    "total", String.valueOf(nodeIds.size())));
        }
        renderNav(inventory, holder, pages, Material.CHEST);
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.module_back"));
    }

    List<String> moduleNodeIds(YamlTextDocument view, EditorModules.ModuleSpec spec) { return new EditorGuiDelegate(this).moduleNodeIds(view, spec); }

    private String[] moduleFieldPath(EditorModules.ModuleSpec spec, String nodeId,
            EditorModules.Field field) { return new EditorGuiDelegate(this).moduleFieldPath(spec, nodeId, field); }

    private Material materialOf(EditorModules.Kind kind) { return new EditorGuiDelegate(this).materialOf(kind); }

    private Double parseDoubleOrNull(String text) { return new EditorGuiDelegate(this).parseDoubleOrNull(text); }

    void renderNav(Inventory inventory, EditorHolder holder, int pages, Material actionMaterial) { new EditorGuiDelegate(this).renderNav(inventory, holder, pages, actionMaterial); }

    void fill(Inventory inventory) { new EditorGuiDelegate(this).fill(inventory); }

    private String pointText(FlowPoint point) { return new EditorGuiDelegate(this).pointText(point); }

    String fieldKey(String key) { return new EditorGuiDelegate(this).fieldKey(key); }

    String valueLine(String value) { return new EditorGuiDelegate(this).valueLine(value); }

    ItemStack item(Material material, String messageKey, List<String> lore, String... replacements) { return new EditorGuiDelegate(this).item(material, messageKey, lore, replacements); }

    ItemStack navItem(Material material, String messageKey, String... replacements) { return new EditorGuiDelegate(this).navItem(material, messageKey, replacements); }

    Map<String, Object> argsMap(String... replacements) { return new EditorGuiDelegate(this).argsMap(replacements); }

    List<String> mapKeys(YamlTextDocument view, String... path) { return new EditorGuiDelegate(this).mapKeys(view, path); }

    int mapSize(Object value) { return new EditorGuiDelegate(this).mapSize(value); }

    List<Object> sequence(YamlTextDocument view, String... path) { return new EditorGuiDelegate(this).sequence(view, path); }

    int sequenceSize(YamlTextDocument view, String... path) { return new EditorGuiDelegate(this).sequenceSize(view, path); }

    private String mapText(Object mapValue, String key, String fallback) { return new EditorGuiDelegate(this).mapText(mapValue, key, fallback); }

    String textOf(Object value, String fallback) { return new EditorGuiDelegate(this).textOf(value, fallback); }

    int pageCount(int size, int pageSize) { return new EditorGuiDelegate(this).pageCount(size, pageSize); }

    int[] pageRange(int page, int size, int pageSize) { return new EditorGuiDelegate(this).pageRange(page, size, pageSize); }

    Integer parseIntOrNull(String text) { return new EditorGuiDelegate(this).parseIntOrNull(text); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) { new EditorGuiDelegate(this).onClick(event); }

    void handleClick(Player player, EditorHolder holder, EditorSession session, int slot) {
        switch (holder.panel) {
            case MAIN -> handleMain(player, holder, session, slot);
            case META -> handleMeta(player, holder, session, slot);
            case WAVES -> handleSpawnList(player, holder, session, slot);
            case SPAWN -> handleSpawnWaves(player, holder, session, slot);
            case WAVE -> handleWave(player, holder, session, slot);
            case GATES -> handleGateList(player, holder, session, slot);
            case GATE -> handleGate(player, holder, session, slot);
            case BRANCHES -> handleOptionList(player, holder, session, slot);
            case OPTION -> handleOption(player, holder, session, slot);
            case CASE -> handleCase(player, holder, session, slot);
            case EXITS -> handleExitList(player, holder, session, slot);
            case MODULES -> handleModules(player, holder, session, slot);
            case MODULE_FIELDS -> handleModuleFields(player, holder, session, slot);
            default -> {
            }
        }
    }

    void handleMain(Player player, EditorHolder holder, EditorSession session, int slot) {
        switch (slot) {
            case SLOT_META -> open(player, Panel.META, new String[0], 0);
            case SLOT_WAVES -> open(player, Panel.WAVES, new String[0], 0);
            case SLOT_GATES -> open(player, Panel.GATES, new String[0], 0);
            case SLOT_BRANCHES -> open(player, Panel.BRANCHES, new String[0], 0);
            case SLOT_MODULES -> open(player, Panel.MODULES, new String[0], 0);
            case SLOT_HELP -> messages.send(player, "editor.help_mark", Map.of());
            case SLOT_SAVE -> {
                player.closeInventory();
                service.save(player);
            }
            case SLOT_DISCARD -> {
                if (holder.discardArmed) {
                    service.discardChanges(player);
                    return;
                }
                holder.discardArmed = true;
                holder.getInventory().setItem(SLOT_DISCARD, navItem(Material.ORANGE_CONCRETE,
                        "editor.gui.discard_confirm"));
            }
            case SLOT_TEST -> {
                player.closeInventory();
                service.startTest(player);
            }
            case SLOT_EXIT -> {
                player.closeInventory();
                service.exit(player);
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    void handleMeta(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleMeta(player, holder, session, slot); }

    void handleSpawnList(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleSpawnList(player, holder, session, slot); }

    void handleSpawnWaves(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleSpawnWaves(player, holder, session, slot); }

    void handleWave(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.SPAWN)) {
            return;
        }
        String spawnId = holder.path[0];
        String waveIndex = holder.path[1];
        String[] wavePath = { "spawn_points", spawnId, "waves", waveIndex };
        switch (slot) {
            case 10 -> prompt(player, holder, "interval", text -> {
                String value = Texts.trim(text);
                if (value.isEmpty()) {
                    invalid(player, holder, value);
                    return;
                }
                applyEdit(player, holder, session, false,
                        doc -> doc.set(value, wavePath(wavePath, "interval")),
                        "field.interval", value);
            });
            case 12 -> {
                boolean current = !Boolean.FALSE.equals(session.flowDoc().view()
                        .value(wavePath(wavePath, "drop_loot")));
                boolean next = !current;
                applyEdit(player, holder, session, false,
                        doc -> doc.set(next, wavePath(wavePath, "drop_loot")),
                        "field.drop_loot", String.valueOf(next));
            }
            case 14 -> prompt(player, holder, "mobs", text -> {
                String value = Texts.trim(text);
                List<Map<String, Object>> mobs = parseMobs(value);
                if (value.isEmpty()) {
                    mobs = new ArrayList<>();
                } else if (mobs.isEmpty()) {
                    invalid(player, holder, value);
                    return;
                }
                List<Map<String, Object>> parsed = mobs;
                applyEdit(player, holder, session, false,
                        doc -> doc.set(parsed, wavePath(wavePath, "mobs")),
                        "field.mobs", String.valueOf(parsed.size()));
            });
            default -> {
            }
        }
    }

    void handleGateList(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleGateList(player, holder, session, slot); }

    void handleGate(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleGate(player, holder, session, slot); }

    void handleExitList(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleExitList(player, holder, session, slot); }

    void handleOptionList(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleOptionList(player, holder, session, slot); }

    void handleOption(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.BRANCHES)) {
            return;
        }
        String optionId = holder.path[0];
        List<String> caseKeys = mapKeys(session.flowDoc().view(), "options", optionId, "cases");
        int[] range = pageRange(holder.page, caseKeys.size(), OPTION_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < OPTION_PAGE_SIZE && index < range[1]) {
            open(player, Panel.CASE, new String[] { optionId, caseKeys.get(index) }, 0);
            return;
        }
        switch (slot) {
            case SLOT_ACTION -> {
                String caseKey = nextCaseKey(session.flowDoc().view(), optionId);
                applyEdit(player, holder, session, false, doc -> {
                    Map<String, Object> optionCase = new LinkedHashMap<>();
                    optionCase.put("label", caseKey);
                    optionCase.put("case", caseKey);
                    optionCase.put("ending", "");
                    optionCase.put("seal_regions", new ArrayList<>());
                    doc.set(optionCase, "options", optionId, "cases", caseKey);
                }, "field.add_case", caseKey);
            }
            case 27 -> {
                String current = textOf(session.flowDoc().view()
                        .value("options", optionId, "select_mode"), "single");
                String next = "multi".equalsIgnoreCase(current) ? "single" : "multi";
                applyEdit(player, holder, session, false,
                        doc -> doc.set(next, "options", optionId, "select_mode"),
                        "field.select_mode", next);
            }
            case 28 -> prompt(player, holder, "time_limit", text -> {
                Integer value = parseIntOrNull(text);
                if (value == null || value < 0) {
                    invalid(player, holder, Texts.trim(text));
                    return;
                }
                applyEdit(player, holder, session, false,
                        doc -> doc.set(value, "options", optionId, "time_limit_seconds"),
                        "field.time_limit", String.valueOf(value));
            });
            case 29 -> prompt(player, holder, "min_select", text -> {
                Integer value = parseIntOrNull(text);
                if (value == null || value < 1) {
                    invalid(player, holder, Texts.trim(text));
                    return;
                }
                applyEdit(player, holder, session, false,
                        doc -> doc.set(value, "options", optionId, "min_select"),
                        "field.min_select", String.valueOf(value));
            });
            case 30 -> prompt(player, holder, "max_select", text -> {
                Integer value = parseIntOrNull(text);
                if (value == null || value < 1) {
                    invalid(player, holder, Texts.trim(text));
                    return;
                }
                applyEdit(player, holder, session, false,
                        doc -> doc.set(value, "options", optionId, "max_select"),
                        "field.max_select", String.valueOf(value));
            });
            case 31 -> {
                Location location = player.getLocation();
                applyEdit(player, holder, session, false, doc -> doc.set(
                        locationMap(location), "options", optionId, "safe-point"),
                        "field.safe_point", "ok");
            }
            case 32 -> prompt(player, holder, "ending_map", text -> {
                String value = Texts.trim(text);
                String[] parts = value.split("=");
                if (parts.length != 2 || Texts.isBlank(parts[0]) || Texts.isBlank(parts[1])) {
                    invalid(player, holder, value);
                    return;
                }
                String key = Texts.trim(parts[0]);
                String ending = Texts.trim(parts[1]);
                applyEdit(player, holder, session, false,
                        doc -> doc.set(ending, "options", optionId, "ending_map", key),
                        "field.ending_map", key + "=" + ending);
            });
            default -> {
            }
        }
    }

    void handleCase(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.OPTION)) {
            return;
        }
        String optionId = holder.path[0];
        String caseKey = holder.path[1];
        switch (slot) {
            case 10 -> prompt(player, holder, "case_label", text -> {
                String value = Texts.trim(text);
                if (value.isEmpty()) {
                    invalid(player, holder, value);
                    return;
                }
                applyEdit(player, holder, session, false,
                        doc -> doc.set(value, "options", optionId, "cases", caseKey, "label"),
                        "field.case_label", value);
            });
            case 12 -> prompt(player, holder, "case_value", text -> {
                String value = Texts.trim(text);
                if (value.isEmpty()) {
                    invalid(player, holder, value);
                    return;
                }
                applyEdit(player, holder, session, false,
                        doc -> doc.set(value, "options", optionId, "cases", caseKey, "case"),
                        "field.case_value", value);
            });
            case 14 -> prompt(player, holder, "case_ending", text -> {
                String value = Texts.trim(text);
                String stored = "-".equals(value) ? "" : value;
                applyEdit(player, holder, session, false,
                        doc -> doc.set(stored, "options", optionId, "cases", caseKey, "ending"),
                        "field.case_ending", stored.isEmpty() ? "-" : stored);
            });
            case SLOT_ACTION -> {
                if (requireCorners(player, session)) {
                    applyEdit(player, holder, session, false, doc ->
                            doc.appendListItem(flatRegion(session),
                                    "options", optionId, "cases", caseKey, "seal_regions"),
                            "field.seal_regions", "1");
                }
            }
            case 18 -> applyEdit(player, holder, session, false, doc -> {
                int last = doc.sequenceSize("options", optionId, "cases", caseKey, "seal_regions") - 1;
                if (last >= 0) {
                    doc.removeListItem(last, "options", optionId, "cases", caseKey, "seal_regions");
                }
            }, "field.seal_regions", "-1");
            default -> {
            }
        }
    }

    boolean requireCorners(Player player, EditorSession session) { return new EditorGuiDelegate(this).requireCorners(player, session); }

    void handleModules(Player player, EditorHolder holder, EditorSession session, int slot) { new EditorGuiDelegate(this).handleModules(player, holder, session, slot); }

    void handleModuleFields(Player player, EditorHolder holder, EditorSession session, int slot) {
        EditorModules.ModuleSpec spec = EditorModules.of(holder.path[0]);
        if (spec == null) {
            return;
        }
        YamlTextDocument view = session.flowDoc().view();
        List<String> nodeIds = moduleNodeIds(view, spec);
        if (slot == SLOT_ACTION) {
            service.mark(player, spec.type(), null);
            reopen(player, holder);
            return;
        }
        if (slot == SLOT_NEXT && holder.page + 1 >= nodeIds.size()) {
            return;
        }
        if (handleCommonNav(player, holder, session, slot, Panel.MODULES)) {
            return;
        }
        if (nodeIds.isEmpty()) {
            return;
        }
        int nodeIndex = Math.max(0, Math.min(holder.page, nodeIds.size() - 1));
        String nodeId = nodeIds.get(nodeIndex);
        List<EditorModules.Field> fields = spec.fields();
        if (slot < 0 || slot >= fields.size()) {
            return;
        }
        EditorModules.Field field = fields.get(slot);
        String[] fieldPath = moduleFieldPath(spec, nodeId, field);
        if (field.kind() == EditorModules.Kind.BOOLEAN) {
            boolean current = !Boolean.FALSE.equals(view.value(fieldPath));
            boolean next = !current;
            applyEdit(player, holder, session, false,
                    doc -> doc.set(next, fieldPath), field.key(), String.valueOf(next));
            return;
        }
        prompt(player, holder, field.key(), text -> {
            String value = Texts.trim(text);
            Object parsed = switch (field.kind()) {
                case NUMBER -> parseIntOrNull(value);
                case DECIMAL -> parseDoubleOrNull(value);
                default -> value;
            };
            if (parsed == null) {
                invalid(player, holder, value);
                return;
            }
            applyEdit(player, holder, session, false,
                    doc -> doc.set(parsed, fieldPath), field.key(), String.valueOf(parsed));
        });
    }

    boolean handleCommonNav(Player player, EditorHolder holder, EditorSession session, int slot,
            Panel backPanel) { return new EditorGuiDelegate(this).handleCommonNav(player, holder, session, slot, backPanel); }

    void prompt(Player player, EditorHolder holder, String fieldKey,
            Consumer<String> handler) {
        String label = messages.message(fieldKey(fieldKey), Map.of());
        messages.send(player, "editor.input_prompt", Map.of("field", label));
        chatInput.await(new ChatInputRequest(plugin, player, 60L, List.of("取消", "cancel"), result -> {
            if (!result.submitted()) {
                reopen(player, holder);
                return;
            }
            handler.accept(Texts.trim(MiniMessages.plainText(result.text())));
        }));
    }

    void invalid(Player player, EditorHolder holder, String value) { new EditorGuiDelegate(this).invalid(player, holder, value); }

    void applyEdit(Player player, EditorHolder holder, EditorSession session, boolean config,
            Consumer<YamlTextDocument> mutation, String fieldKey, String value) {
        EditorDocument doc = config ? session.configDoc() : session.flowDoc();
        EditorDocument.SaveResult result;
        synchronized (doc) {
            result = doc.mutate(mutation);
        }
        if (result.saved()) {
            session.dirty(true);
            messages.send(player, "editor.field_updated", Map.of(
                    "field", messages.message(fieldKey(fieldKey), Map.of()),
                    "value", value));
        } else {
            messages.send(player, "editor.field_failed", Map.of("error", result.detail()));
        }
        reopen(player, holder);
    }

    private String[] wavePath(String[] base, String key) { return new EditorGuiDelegate(this).wavePath(base, key); }

    private Map<String, Object> locationMap(Location location) { return new EditorGuiDelegate(this).locationMap(location); }

    Map<String, Object> flatRegion(EditorSession session) { return new EditorGuiDelegate(this).flatRegion(session); }

    private String nextCaseKey(YamlTextDocument view, String optionId) { return new EditorGuiDelegate(this).nextCaseKey(view, optionId); }

    private List<Map<String, Object>> parseMobs(String text) { return new EditorGuiDelegate(this).parseMobs(text); }

    int parsePositive(String raw, int fallback) { return new EditorGuiDelegate(this).parsePositive(raw, fallback); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) { new EditorGuiDelegate(this).onDrag(event); }
}
