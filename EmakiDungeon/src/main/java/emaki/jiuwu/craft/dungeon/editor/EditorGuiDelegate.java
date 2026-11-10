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

final class EditorGuiDelegate {

    private final EditorGui ownerRef;

    EditorGuiDelegate(EditorGui ownerRef) {
        this.ownerRef = ownerRef;
    }

    void openMain(Player player) {
        ownerRef.open(player, EditorGui.Panel.MAIN, new String[0], 0);
    }

    void closeIfEditor(Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof EditorGui.EditorHolder) {
            player.closeInventory();
        }
    }

    void open(Player player, EditorGui.Panel panel, String[] path, int page) {
        EditorGui.EditorHolder holder = new EditorGui.EditorHolder(player.getUniqueId(), panel, path, page);
        Inventory inventory = ownerRef.render(player, holder);
        holder.inventory = inventory;
        player.openInventory(inventory);
    }

    void reopen(Player player, EditorGui.EditorHolder holder) {
        EditorSession session = ownerRef.service.sessionOf(holder.playerId);
        if (session == null) {
            player.closeInventory();
            return;
        }
        ownerRef.open(player, holder.panel, holder.path, holder.page);
    }

    void renderMeta(Inventory inventory, EditorSession session, EditorGui.EditorHolder holder) {
        YamlTextDocument view = session.configDoc().view();
        for (int index = 0; index < EditorGui.META_FIELDS.size(); index++) {
            EditorGui.MetaField field = EditorGui.META_FIELDS.get(index);
            Object current = view.value(field.path());
            inventory.setItem(index, item(field.material(), ownerRef.fieldKey(field.key()),
                    List.of(ownerRef.valueLine(current == null ? "" : String.valueOf(current)))));
        }
        ownerRef.renderNav(inventory, holder, 1, null);
        inventory.setItem(EditorGui.SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    void renderWave(Inventory inventory, EditorSession session, EditorGui.EditorHolder holder) {
        String spawnId = holder.path[0];
        YamlTextDocument view = session.flowDoc().view();
        Object interval = view.value("spawn_points", spawnId, "waves", holder.path[1], "interval");
        boolean drop = !Boolean.FALSE.equals(
                view.value("spawn_points", spawnId, "waves", holder.path[1], "drop_loot"));
        int mobs = ownerRef.sequenceSize(view, "spawn_points", spawnId, "waves", holder.path[1], "mobs");
        inventory.setItem(10, ownerRef.item(Material.CLOCK, "editor.gui.field.interval",
                List.of(ownerRef.valueLine(interval == null ? "2s" : String.valueOf(interval)))));
        inventory.setItem(12, ownerRef.navItem(drop ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                drop ? "editor.gui.field.drop_loot_on" : "editor.gui.field.drop_loot_off"));
        inventory.setItem(14, ownerRef.item(Material.BONE, "editor.gui.field.mobs",
                List.of(ownerRef.valueLine(ownerRef.messages.message("editor.gui.mob_count",
                        Map.of("count", String.valueOf(mobs)))))));
        ownerRef.renderNav(inventory, holder, 1, null);
        inventory.setItem(EditorGui.SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_spawn"));
    }

    void renderOptionList(Inventory inventory, EditorSession session, EditorGui.EditorHolder holder) {
        List<String> ids = ownerRef.mapKeys(session.flowDoc().view(), "options");
        int[] range = ownerRef.pageRange(holder.page, ids.size(), EditorGui.LIST_PAGE_SIZE);
        for (int index = range[0]; index < range[1]; index++) {
            String id = ids.get(index);
            String mode = ownerRef.textOf(session.flowDoc().view().value("options", id, "select_mode"), "single");
            inventory.setItem(index - range[0], item(Material.SPRUCE_SAPLING, id,
                    List.of(ownerRef.valueLine(mode))));
        }
        int pages = ownerRef.pageCount(ids.size(), EditorGui.LIST_PAGE_SIZE);
        ownerRef.renderNav(inventory, holder, pages, Material.COMPASS);
        inventory.setItem(EditorGui.SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    void renderModules(Inventory inventory, EditorSession session, EditorGui.EditorHolder holder) {
        YamlTextDocument view = session.flowDoc().view();
        List<EditorModules.ModuleSpec> specs = EditorModules.all();
        for (int index = 0; index < specs.size(); index++) {
            EditorModules.ModuleSpec spec = specs.get(index);
            int count = ownerRef.moduleNodeIds(view, spec).size();
            String label = ownerRef.messages.message("editor.marker." + spec.type(), Map.of());
            String countLine = ownerRef.messages.message("editor.gui.module_count",
                    Map.of("count", String.valueOf(count)));
            inventory.setItem(index, item(Material.CHEST, "editor.gui.module_item",
                    List.of(countLine, spec.section()), "type", label));
        }
        ownerRef.renderNav(inventory, holder, 1, null);
        inventory.setItem(EditorGui.SLOT_BACK, navItem(Material.OAK_DOOR, "editor.gui.back_main"));
    }

    List<String> moduleNodeIds(YamlTextDocument view, EditorModules.ModuleSpec spec) {
        Object raw = view.value(spec.section());
        if (!(raw instanceof Map<?, ?> map)) {
            return new ArrayList<>();
        }
        if (spec.single()) {
            return new ArrayList<>(List.of(""));
        }
        List<String> ids = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            ids.add(String.valueOf(key));
        }
        return ids;
    }

    String[] moduleFieldPath(EditorModules.ModuleSpec spec, String nodeId, EditorModules.Field field) {
        int offset = spec.single() ? 1 : 2;
        String[] result = new String[offset + field.path().length];
        result[0] = spec.section();
        if (!spec.single()) {
            result[1] = nodeId;
        }
        System.arraycopy(field.path(), 0, result, offset, field.path().length);
        return result;
    }

    Material materialOf(EditorModules.Kind kind) {
        return switch (kind) {
            case TEXT -> Material.PAPER;
            case NUMBER, DECIMAL -> Material.CLOCK;
            case BOOLEAN -> Material.LEVER;
        };
    }

    Double parseDoubleOrNull(String text) {
        try {
            return Double.parseDouble(Texts.trim(text));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    void renderNav(Inventory inventory, EditorGui.EditorHolder holder, int pages, Material actionMaterial) {
        if (pages > 1) {
            inventory.setItem(EditorGui.SLOT_PREV, navItem(Material.ARROW, "editor.gui.prev"));
            inventory.setItem(EditorGui.SLOT_NEXT, navItem(Material.ARROW, "editor.gui.next"));
            inventory.setItem(EditorGui.SLOT_INFO, navItem(Material.BOOK, "editor.gui.page",
                    "page", String.valueOf(holder.page + 1),
                    "total", String.valueOf(pages)));
        }
        if (actionMaterial != null) {
            inventory.setItem(EditorGui.SLOT_ACTION, navItem(actionMaterial, "editor.gui.add"));
        }
        inventory.setItem(EditorGui.SLOT_CLOSE, navItem(Material.BARRIER, "editor.gui.close"));
    }

    void fill(Inventory inventory) {
        for (int slot = 0; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, navItem(Material.GRAY_STAINED_GLASS_PANE, "editor.gui.filler"));
            }
        }
    }

    String pointText(FlowPoint point) {
        return point == null ? "-" : point.x() + "," + point.y() + "," + point.z();
    }

    String fieldKey(String key) {
        return "editor.gui.field." + key;
    }

    String valueLine(String value) {
        return ownerRef.messages.message("editor.gui.value", Map.of("value", value == null ? "" : value));
    }

    ItemStack item(Material material, String messageKey, List<String> lore, String... replacements) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessages.parse(ownerRef.messages.message(messageKey, argsMap(replacements))));
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

    ItemStack navItem(Material material, String messageKey, String... replacements) {
        return ownerRef.item(material, messageKey, List.of(), replacements);
    }

    Map<String, Object> argsMap(String... replacements) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            args.put(replacements[index], replacements[index + 1]);
        }
        return args;
    }

    List<String> mapKeys(YamlTextDocument view, String... path) {
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

    int mapSize(Object value) {
        return value instanceof Map<?, ?> map ? map.size() : 0;
    }

    List<Object> sequence(YamlTextDocument view, String... path) {
        Object value = view.value(path);
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    int sequenceSize(YamlTextDocument view, String... path) {
        Object value = view.value(path);
        return value instanceof List<?> list ? list.size() : 0;
    }

    String mapText(Object mapValue, String key, String fallback) {
        if (mapValue instanceof Map<?, ?> map && map.get(key) != null) {
            return String.valueOf(map.get(key));
        }
        return fallback;
    }

    String textOf(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    int pageCount(int size, int pageSize) {
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    int[] pageRange(int page, int size, int pageSize) {
        int pages = ownerRef.pageCount(size, pageSize);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, size);
        if (from > to) {
            from = to;
        }
        return new int[] { from, to };
    }

    Integer parseIntOrNull(String text) {
        try {
            return Integer.parseInt(Texts.trim(text));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorGui.EditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        EditorSession session = ownerRef.service.sessionOf(holder.playerId);
        if (session == null) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ownerRef.handleClick(player, holder, session, event.getRawSlot());
    }

    void handleMeta(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.MAIN)) {
            return;
        }
        if (slot < 0 || slot >= EditorGui.META_FIELDS.size()) {
            return;
        }
        EditorGui.MetaField field = EditorGui.META_FIELDS.get(slot);
        ownerRef.prompt(player, holder, field.key(), text -> {
            String value = Texts.trim(text);
            Object parsed = field.numeric() ? ownerRef.parseIntOrNull(value) : value;
            if (parsed == null) {
                ownerRef.invalid(player, holder, value);
                return;
            }
            ownerRef.applyEdit(player, holder, session, true, doc -> doc.set(parsed, field.path()),
                    field.key(), String.valueOf(parsed));
        });
    }

    void handleSpawnList(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.MAIN)) {
            return;
        }
        if (slot == EditorGui.SLOT_ACTION) {
            ownerRef.service.mark(player, "spawn", null);
            ownerRef.reopen(player, holder);
            return;
        }
        List<String> ids = ownerRef.mapKeys(session.flowDoc().view(), "spawn_points");
        int[] range = ownerRef.pageRange(holder.page, ids.size(), EditorGui.LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < EditorGui.CONTENT_SLOTS && index < range[1]) {
            ownerRef.open(player, EditorGui.Panel.SPAWN, new String[] { ids.get(index) }, 0);
        }
    }

    void handleSpawnWaves(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.WAVES)) {
            return;
        }
        String spawnId = holder.path[0];
        if (slot == EditorGui.SLOT_ACTION) {
            ownerRef.applyEdit(player, holder, session, false, doc -> {
                Map<String, Object> wave = new LinkedHashMap<>();
                wave.put("interval", "2s");
                wave.put("drop_loot", true);
                wave.put("mobs", new ArrayList<>());
                doc.appendListItem(wave, "spawn_points", spawnId, "waves");
            }, "field.add_wave", "1");
            return;
        }
        List<Object> waves = ownerRef.sequence(session.flowDoc().view(), "spawn_points", spawnId, "waves");
        int[] range = ownerRef.pageRange(holder.page, waves.size(), EditorGui.LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < EditorGui.CONTENT_SLOTS && index < range[1]) {
            ownerRef.open(player, EditorGui.Panel.WAVE, new String[] { spawnId, String.valueOf(index) }, 0);
        }
    }

    void handleGateList(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.MAIN)) {
            return;
        }
        if (slot == EditorGui.SLOT_ACTION) {
            ownerRef.service.mark(player, "gate", null);
            ownerRef.reopen(player, holder);
            return;
        }
        List<String> ids = ownerRef.mapKeys(session.flowDoc().view(), "gates");
        int[] range = ownerRef.pageRange(holder.page, ids.size(), EditorGui.LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < EditorGui.CONTENT_SLOTS && index < range[1]) {
            ownerRef.open(player, EditorGui.Panel.GATE, new String[] { ids.get(index) }, 0);
        }
    }

    void handleGate(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.GATES)) {
            return;
        }
        String gateId = holder.path[0];
        switch (slot) {
            case 10 -> {
                String current = ownerRef.textOf(session.flowDoc().view().value("gates", gateId, "logic"), "AND");
                String next = "AND".equalsIgnoreCase(current) ? "OR" : "AND";
                ownerRef.applyEdit(player, holder, session, false,
                        doc -> doc.set(next, "gates", gateId, "logic"), "field.logic", next);
            }
            case 12 -> ownerRef.prompt(player, holder, "open_mode", text -> {
                String value = Texts.trim(text);
                if (value.isEmpty()) {
                    ownerRef.invalid(player, holder, value);
                    return;
                }
                ownerRef.applyEdit(player, holder, session, false,
                        doc -> doc.set(value, "gates", gateId, "open_mode"), "field.open_mode", value);
            });
            case EditorGui.SLOT_ACTION -> {
                if (ownerRef.requireCorners(player, session)) {
                    ownerRef.applyEdit(player, holder, session, false, doc ->
                            doc.appendListItem(ownerRef.flatRegion(session), "gates", gateId, "door_regions"),
                            "field.door_regions", "1");
                }
            }
            case 18 -> ownerRef.applyEdit(player, holder, session, false, doc -> {
                int last = doc.sequenceSize("gates", gateId, "door_regions") - 1;
                if (last >= 0) {
                    doc.removeListItem(last, "gates", gateId, "door_regions");
                }
            }, "field.door_regions", "-1");
            default -> {
            }
        }
    }

    void handleExitList(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.MAIN)) {
            return;
        }
        if (slot == EditorGui.SLOT_ACTION) {
            ownerRef.service.mark(player, "exit", null);
            ownerRef.reopen(player, holder);
            return;
        }
        List<String> ids = ownerRef.mapKeys(session.flowDoc().view(), "exit_points");
        int[] range = ownerRef.pageRange(holder.page, ids.size(), EditorGui.LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < EditorGui.CONTENT_SLOTS && index < range[1]) {
            String exitId = ids.get(index);
            Object confirm = session.flowDoc().view().value("exit_points", exitId, "need_confirm");
            boolean next = Boolean.FALSE.equals(confirm);
            ownerRef.applyEdit(player, holder, session, false,
                    doc -> doc.set(next, "exit_points", exitId, "need_confirm"),
                    next ? "need_confirm_on" : "need_confirm_off",
                    String.valueOf(next));
        }
    }

    void handleOptionList(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.MAIN)) {
            return;
        }
        if (slot == EditorGui.SLOT_ACTION) {
            ownerRef.service.mark(player, "option", null);
            ownerRef.reopen(player, holder);
            return;
        }
        List<String> ids = ownerRef.mapKeys(session.flowDoc().view(), "options");
        int[] range = ownerRef.pageRange(holder.page, ids.size(), EditorGui.LIST_PAGE_SIZE);
        int index = range[0] + slot;
        if (slot >= 0 && slot < EditorGui.CONTENT_SLOTS && index < range[1]) {
            ownerRef.open(player, EditorGui.Panel.OPTION, new String[] { ids.get(index) }, 0);
        }
    }

    boolean requireCorners(Player player, EditorSession session) {
        if (session.pos1() == null) {
            ownerRef.messages.send(player, "editor.pos_missing", Map.of("which", "1"));
            return false;
        }
        if (session.pos2() == null) {
            ownerRef.messages.send(player, "editor.pos_missing", Map.of("which", "2"));
            return false;
        }
        return true;
    }

    void handleModules(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot) {
        if (ownerRef.handleCommonNav(player, holder, session, slot, EditorGui.Panel.MAIN)) {
            return;
        }
        List<EditorModules.ModuleSpec> specs = EditorModules.all();
        if (slot < 0 || slot >= specs.size()) {
            return;
        }
        ownerRef.open(player, EditorGui.Panel.MODULE_FIELDS, new String[] { specs.get(slot).type() }, 0);
    }

    boolean handleCommonNav(Player player, EditorGui.EditorHolder holder, EditorSession session, int slot, EditorGui.Panel backPanel) {
        if (slot == EditorGui.SLOT_CLOSE) {
            player.closeInventory();
            return true;
        }
        if (slot == EditorGui.SLOT_BACK) {
            String[] backPath = holder.panel == EditorGui.Panel.WAVE || holder.panel == EditorGui.Panel.CASE
                    ? new String[] { holder.path[0] }
                    : new String[0];
            ownerRef.open(player, backPanel, backPath, 0);
            return true;
        }
        if (slot == EditorGui.SLOT_PREV && holder.page > 0) {
            ownerRef.open(player, holder.panel, holder.path, holder.page - 1);
            return true;
        }
        if (slot == EditorGui.SLOT_NEXT) {
            ownerRef.open(player, holder.panel, holder.path, holder.page + 1);
            return true;
        }
        return false;
    }

    void invalid(Player player, EditorGui.EditorHolder holder, String value) {
        ownerRef.messages.send(player, "editor.input_invalid", Map.of("value", value));
        ownerRef.reopen(player, holder);
    }

    String[] wavePath(String[] base, String key) {
        String[] result = new String[base.length + 1];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = key;
        return result;
    }

    Map<String, Object> locationMap(Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    Map<String, Object> flatRegion(EditorSession session) {
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

    String nextCaseKey(YamlTextDocument view, String optionId) {
        int size = ownerRef.mapSize(view.value("options", optionId, "cases"));
        for (int index = size + 1; index < size + 512; index++) {
            String candidate = "case" + index;
            if (view.value("options", optionId, "cases", candidate) == null) {
                return candidate;
            }
        }
        return "case" + System.currentTimeMillis();
    }

    List<Map<String, Object>> parseMobs(String text) {
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
            int count = parts.length > 1 ? ownerRef.parsePositive(parts[1], 1) : 1;
            int weight = parts.length > 2 ? ownerRef.parsePositive(parts[2], 1) : 1;
            Map<String, Object> mob = new LinkedHashMap<>();
            mob.put("id", mobId);
            mob.put("count", count);
            mob.put("weight", weight);
            mobs.add(mob);
        }
        return mobs;
    }

    int parsePositive(String raw, int fallback) {
        Integer value = ownerRef.parseIntOrNull(raw);
        return value == null || value < 1 ? fallback : value;
    }

    void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EditorGui.EditorHolder) {
            event.setCancelled(true);
        }
    }

}
