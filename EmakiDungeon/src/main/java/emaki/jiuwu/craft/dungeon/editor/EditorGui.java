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

    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_ACTION = 46;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_INFO = 50;
    private static final int SLOT_CLOSE = 53;
    private static final int SLOT_HELP = 14;
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
    private static final int OPTION_PAGE_SIZE = 27;
    private static final int LIST_PAGE_SIZE = 45;

    enum Panel {
        MAIN, META, WAVES, SPAWN, WAVE, GATES, GATE, BRANCHES, OPTION, CASE
    }

    static final class EditorHolder implements InventoryHolder {

        private final UUID playerId;
        private final Panel panel;
        private final String[] path;
        private final int page;
        private boolean discardArmed;
        private Inventory inventory;

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

    private record MetaField(String key, Material material, String[] path, boolean numeric) {
    }

    private static final List<MetaField> META_FIELDS = List.of(
            new MetaField("display_name", Material.NAME_TAG, new String[] { "display-name" }, false),
            new MetaField("description", Material.PAPER, new String[] { "description" }, false),
            new MetaField("difficulty_group", Material.EMERALD, new String[] { "difficulty-group" }, false),
            new MetaField("prepare_window", Material.CLOCK, new String[] { "enter", "prepare-window-seconds" }, true),
            new MetaField("pool_max", Material.HOPPER, new String[] { "pool", "max-worlds" }, true),
            new MetaField("leaderboard_max", Material.GOLDEN_HELMET,
                    new String[] { "leaderboard", "max-entries" }, true)
    );

    private final Plugin plugin;
    private final EditorService service;
    private final MessageService messages;
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

    public void openMain(Player player) {
        open(player, Panel.MAIN, new String[0], 0);
    }

    public void closeIfEditor(Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof EditorHolder) {
            player.closeInventory();
        }
    }

    private void open(Player player, Panel panel, String[] path, int page) {
        EditorHolder holder = new EditorHolder(player.getUniqueId(), panel, path, page);
        Inventory inventory = render(player, holder);
        holder.inventory = inventory;
        player.openInventory(inventory);
    }

    private void reopen(Player player, EditorHolder holder) {
        EditorSession session = service.sessionOf(holder.playerId);
        if (session == null) {
            player.closeInventory();
            return;
        }
        open(player, holder.panel, holder.path, holder.page);
    }

    private Inventory render(Player player, EditorHolder holder) {
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
            default -> {
            }
        }
        fill(inventory);
        return inventory;
    }

    private void renderMain(Inventory inventory, EditorSession session, EditorHolder holder) {
        inventory.setItem(SLOT_META, navItem(Material.BOOK, "editor.gui.meta"));
        inventory.setItem(SLOT_WAVES, navItem(Material.REDSTONE, "editor.gui.waves"));
        inventory.setItem(SLOT_GATES, navItem(Material.IRON_DOOR, "editor.gui.gates"));
        inventory.setItem(SLOT_BRANCHES, navItem(Material.SPRUCE_SAPLING, "editor.gui.branches"));
        inventory.setItem(SLOT_HELP, navItem(Material.COMPASS, "editor.gui.help"));
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

    private void renderMeta(Inventory inventory, EditorSession session, EditorHolder holder) {
        YamlTextDocument view = session.configDoc().view();
        for (int index = 0; index < META_FIELDS.size(); index++) {
            MetaField field = META_FIELDS.get(index);
            Object current = view.value(field.path());
            inventory.setItem(index, item(field.material(), fieldKey(field.key()),
                    List.of(valueLine(current == null ? "" : String.valueOf(current)))));
        }
        renderNav(inventory, holder, 1, null);
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    private void renderSpawnList(Inventory inventory, EditorSession session, EditorHolder holder) {
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

    private void renderSpawnWaves(Inventory inventory, EditorSession session, EditorHolder holder) {
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

    private void renderWave(Inventory inventory, EditorSession session, EditorHolder holder) {
        String spawnId = holder.path[0];
        YamlTextDocument view = session.flowDoc().view();
        Object interval = view.value("spawn_points", spawnId, "waves", holder.path[1], "interval");
        boolean drop = !Boolean.FALSE.equals(
                view.value("spawn_points", spawnId, "waves", holder.path[1], "drop_loot"));
        int mobs = sequenceSize(view, "spawn_points", spawnId, "waves", holder.path[1], "mobs");
        inventory.setItem(10, item(Material.CLOCK, "editor.gui.field.interval",
                List.of(valueLine(interval == null ? "2s" : String.valueOf(interval)))));
        inventory.setItem(12, navItem(drop ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                drop ? "editor.gui.field.drop_loot_on" : "editor.gui.field.drop_loot_off"));
        inventory.setItem(14, item(Material.BONE, "editor.gui.field.mobs",
                List.of(valueLine(messages.message("editor.gui.mob_count",
                        Map.of("count", String.valueOf(mobs)))))));
        renderNav(inventory, holder, 1, null);
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_spawn"));
    }

    private void renderGateList(Inventory inventory, EditorSession session, EditorHolder holder) {
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

    private void renderGate(Inventory inventory, EditorSession session, EditorHolder holder) {
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

    private void renderOptionList(Inventory inventory, EditorSession session, EditorHolder holder) {
        List<String> ids = mapKeys(session.flowDoc().view(), "options");
        int[] range = pageRange(holder.page, ids.size(), LIST_PAGE_SIZE);
        for (int index = range[0]; index < range[1]; index++) {
            String id = ids.get(index);
            String mode = textOf(session.flowDoc().view().value("options", id, "select_mode"), "single");
            inventory.setItem(index - range[0], item(Material.SPRUCE_SAPLING, id,
                    List.of(valueLine(mode))));
        }
        int pages = pageCount(ids.size(), LIST_PAGE_SIZE);
        renderNav(inventory, holder, pages, Material.COMPASS);
        inventory.setItem(SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    private void renderOption(Inventory inventory, EditorSession session, EditorHolder holder) {
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

    private void renderCase(Inventory inventory, EditorSession session, EditorHolder holder) {
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

    private void renderNav(Inventory inventory, EditorHolder holder, int pages, Material actionMaterial) {
        if (pages > 1) {
            inventory.setItem(SLOT_PREV, navItem(Material.ARROW, "editor.gui.prev"));
            inventory.setItem(SLOT_NEXT, navItem(Material.ARROW, "editor.gui.next"));
            inventory.setItem(SLOT_INFO, navItem(Material.BOOK, "editor.gui.page",
                    "page", String.valueOf(holder.page + 1),
                    "total", String.valueOf(pages)));
        }
        if (actionMaterial != null) {
            inventory.setItem(SLOT_ACTION, navItem(actionMaterial, "editor.gui.add"));
        }
        inventory.setItem(SLOT_CLOSE, navItem(Material.BARRIER, "editor.gui.close"));
    }

    private void fill(Inventory inventory) {
        for (int slot = 0; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, navItem(Material.GRAY_STAINED_GLASS_PANE, "editor.gui.filler"));
            }
        }
    }

    private String pointText(FlowPoint point) {
        return point == null ? "-" : point.x() + "," + point.y() + "," + point.z();
    }

    private String fieldKey(String key) {
        return "editor.gui.field." + key;
    }

    private String valueLine(String value) {
        return messages.message("editor.gui.value", Map.of("value", value == null ? "" : value));
    }

    private ItemStack item(Material material, String messageKey, List<String> lore, String... replacements) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessages.parse(messages.message(messageKey, argsMap(replacements))));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(MiniMessages.parse(line));
            }
            meta.lore(lines);
        }
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("emakidungeon", "editor_nav"),
                PersistentDataType.STRING, messageKey);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack navItem(Material material, String messageKey, String... replacements) {
        return item(material, messageKey, List.of(), replacements);
    }

    private Map<String, Object> argsMap(String... replacements) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            args.put(replacements[index], replacements[index + 1]);
        }
        return args;
    }

    private List<String> mapKeys(YamlTextDocument view, String... path) {
        Object value = view.value(path);
        if (!(value instanceof Map<?, ?> map)) {
            return new ArrayList<>();
        }
        List<String> keys = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    private int mapSize(Object value) {
        return value instanceof Map<?, ?> map ? map.size() : 0;
    }

    private List<Object> sequence(YamlTextDocument view, String... path) {
        Object value = view.value(path);
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    private int sequenceSize(YamlTextDocument view, String... path) {
        Object value = view.value(path);
        return value instanceof List<?> list ? list.size() : 0;
    }

    private String mapText(Object mapValue, String key, String fallback) {
        if (mapValue instanceof Map<?, ?> map && map.get(key) != null) {
            return String.valueOf(map.get(key));
        }
        return fallback;
    }

    private String textOf(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int pageCount(int size, int pageSize) {
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    private int[] pageRange(int page, int size, int pageSize) {
        int pages = pageCount(size, pageSize);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, size);
        if (from > to) {
            from = to;
        }
        return new int[] { from, to };
    }

    private Integer parseIntOrNull(String text) {
        try {
            return Integer.parseInt(Texts.trim(text));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        EditorSession session = service.sessionOf(holder.playerId);
        if (session == null) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        handleClick(player, holder, session, event.getRawSlot());
    }

    private void handleClick(Player player, EditorHolder holder, EditorSession session, int slot) {
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
            default -> {
            }
        }
    }

    private void handleMain(Player player, EditorHolder holder, EditorSession session, int slot) {
        switch (slot) {
            case SLOT_META -> open(player, Panel.META, new String[0], 0);
            case SLOT_WAVES -> open(player, Panel.WAVES, new String[0], 0);
            case SLOT_GATES -> open(player, Panel.GATES, new String[0], 0);
            case SLOT_BRANCHES -> open(player, Panel.BRANCHES, new String[0], 0);
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

    private void handleMeta(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.MAIN)) {
            return;
        }
        if (slot < 0 || slot >= META_FIELDS.size()) {
            return;
        }
        MetaField field = META_FIELDS.get(slot);
        prompt(player, holder, field.key(), text -> {
            String value = Texts.trim(text);
            Object parsed = field.numeric() ? parseIntOrNull(value) : value;
            if (parsed == null) {
                invalid(player, holder, value);
                return;
            }
            applyEdit(player, holder, session, true, doc -> doc.set(parsed, field.path()),
                    field.key(), String.valueOf(parsed));
        });
    }

    private void handleSpawnList(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.MAIN)) {
            return;
        }
        if (slot == SLOT_ACTION) {
            service.mark(player, "spawn", null);
            reopen(player, holder);
            return;
        }
        List<String> ids = mapKeys(session.flowDoc().view(), "spawn_points");
        int[] range = pageRange(holder.page, ids.size(), LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < CONTENT_SLOTS && index < range[1]) {
            open(player, Panel.SPAWN, new String[] { ids.get(index) }, 0);
        }
    }

    private void handleSpawnWaves(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.WAVES)) {
            return;
        }
        String spawnId = holder.path[0];
        if (slot == SLOT_ACTION) {
            applyEdit(player, holder, session, false, doc -> {
                Map<String, Object> wave = new LinkedHashMap<>();
                wave.put("interval", "2s");
                wave.put("drop_loot", true);
                wave.put("mobs", new ArrayList<>());
                doc.appendListItem(wave, "spawn_points", spawnId, "waves");
            }, "field.add_wave", "1");
            return;
        }
        List<Object> waves = sequence(session.flowDoc().view(), "spawn_points", spawnId, "waves");
        int[] range = pageRange(holder.page, waves.size(), LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < CONTENT_SLOTS && index < range[1]) {
            open(player, Panel.WAVE, new String[] { spawnId, String.valueOf(index) }, 0);
        }
    }

    private void handleWave(Player player, EditorHolder holder, EditorSession session, int slot) {
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

    private void handleGateList(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.MAIN)) {
            return;
        }
        if (slot == SLOT_ACTION) {
            service.mark(player, "gate", null);
            reopen(player, holder);
            return;
        }
        List<String> ids = mapKeys(session.flowDoc().view(), "gates");
        int[] range = pageRange(holder.page, ids.size(), LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < CONTENT_SLOTS && index < range[1]) {
            open(player, Panel.GATE, new String[] { ids.get(index) }, 0);
        }
    }

    private void handleGate(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.GATES)) {
            return;
        }
        String gateId = holder.path[0];
        switch (slot) {
            case 10 -> {
                String current = textOf(session.flowDoc().view().value("gates", gateId, "logic"), "AND");
                String next = "AND".equalsIgnoreCase(current) ? "OR" : "AND";
                applyEdit(player, holder, session, false,
                        doc -> doc.set(next, "gates", gateId, "logic"), "field.logic", next);
            }
            case 12 -> prompt(player, holder, "open_mode", text -> {
                String value = Texts.trim(text);
                if (value.isEmpty()) {
                    invalid(player, holder, value);
                    return;
                }
                applyEdit(player, holder, session, false,
                        doc -> doc.set(value, "gates", gateId, "open_mode"), "field.open_mode", value);
            });
            case SLOT_ACTION -> {
                if (requireCorners(player, session)) {
                    applyEdit(player, holder, session, false, doc ->
                            doc.appendListItem(flatRegion(session), "gates", gateId, "door_regions"),
                            "field.door_regions", "1");
                }
            }
            case 18 -> applyEdit(player, holder, session, false, doc -> {
                int last = doc.sequenceSize("gates", gateId, "door_regions") - 1;
                if (last >= 0) {
                    doc.removeListItem(last, "gates", gateId, "door_regions");
                }
            }, "field.door_regions", "-1");
            default -> {
            }
        }
    }

    private void handleOptionList(Player player, EditorHolder holder, EditorSession session, int slot) {
        if (handleCommonNav(player, holder, session, slot, Panel.MAIN)) {
            return;
        }
        if (slot == SLOT_ACTION) {
            service.mark(player, "option", null);
            reopen(player, holder);
            return;
        }
        List<String> ids = mapKeys(session.flowDoc().view(), "options");
        int[] range = pageRange(holder.page, ids.size(), LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < CONTENT_SLOTS && index < range[1]) {
            open(player, Panel.OPTION, new String[] { ids.get(index) }, 0);
        }
    }

    private void handleOption(Player player, EditorHolder holder, EditorSession session, int slot) {
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

    private void handleCase(Player player, EditorHolder holder, EditorSession session, int slot) {
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

    private boolean requireCorners(Player player, EditorSession session) {
        if (session.pos1() == null) {
            messages.send(player, "editor.pos_missing", Map.of("which", "1"));
            return false;
        }
        if (session.pos2() == null) {
            messages.send(player, "editor.pos_missing", Map.of("which", "2"));
            return false;
        }
        return true;
    }

    private boolean handleCommonNav(Player player, EditorHolder holder, EditorSession session, int slot,
            Panel backPanel) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return true;
        }
        if (slot == SLOT_BACK) {
            String[] backPath = holder.panel == Panel.WAVE || holder.panel == Panel.CASE
                    ? new String[] { holder.path[0] }
                    : new String[0];
            open(player, backPanel, backPath, 0);
            return true;
        }
        if (slot == SLOT_PREV && holder.page > 0) {
            open(player, holder.panel, holder.path, holder.page - 1);
            return true;
        }
        if (slot == SLOT_NEXT) {
            open(player, holder.panel, holder.path, holder.page + 1);
            return true;
        }
        return false;
    }

    private void prompt(Player player, EditorHolder holder, String fieldKey,
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

    private void invalid(Player player, EditorHolder holder, String value) {
        messages.send(player, "editor.input_invalid", Map.of("value", value));
        reopen(player, holder);
    }

    private void applyEdit(Player player, EditorHolder holder, EditorSession session, boolean config,
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

    private String[] wavePath(String[] base, String key) {
        String[] result = new String[base.length + 1];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = key;
        return result;
    }

    private Map<String, Object> locationMap(Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private Map<String, Object> flatRegion(EditorSession session) {
        FlowPoint first = session.pos1();
        FlowPoint second = session.pos2();
        long ax = (long) Math.floor(first == null ? 0 : first.x());
        long ay = (long) Math.floor(first == null ? 0 : first.y());
        long az = (long) Math.floor(first == null ? 0 : first.z());
        long bx = (long) Math.floor(second == null ? 0 : second.x());
        long by = (long) Math.floor(second == null ? 0 : second.y());
        long bz = (long) Math.floor(second == null ? 0 : second.z());
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("min.x", Math.min(ax, bx));
        region.put("min.y", Math.min(ay, by));
        region.put("min.z", Math.min(az, bz));
        region.put("max.x", Math.max(ax, bx));
        region.put("max.y", Math.max(ay, by));
        region.put("max.z", Math.max(az, bz));
        return region;
    }

    private String nextCaseKey(YamlTextDocument view, String optionId) {
        int size = mapSize(view.value("options", optionId, "cases"));
        for (int index = size + 1; index < size + 512; index++) {
            String candidate = "case" + index;
            if (view.value("options", optionId, "cases", candidate) == null) {
                return candidate;
            }
        }
        return "case" + System.currentTimeMillis();
    }

    private List<Map<String, Object>> parseMobs(String text) {
        List<Map<String, Object>> mobs = new ArrayList<>();
        if (Texts.isBlank(text)) {
            return mobs;
        }
        for (String token : text.split("[,;]")) {
            String trimmed = Texts.trim(token);
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":");
            String mobId = Texts.trim(parts[0]);
            if (mobId.isEmpty()) {
                return new ArrayList<>();
            }
            int count = parts.length > 1 ? parsePositive(parts[1], 1) : 1;
            int weight = parts.length > 2 ? parsePositive(parts[2], 1) : 1;
            Map<String, Object> mob = new LinkedHashMap<>();
            mob.put("id", mobId);
            mob.put("count", count);
            mob.put("weight", weight);
            mobs.add(mob);
        }
        return mobs;
    }

    private int parsePositive(String raw, int fallback) {
        Integer value = parseIntOrNull(raw);
        return value == null || value < 1 ? fallback : value;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EditorHolder) {
            event.setCancelled(true);
        }
    }
}
