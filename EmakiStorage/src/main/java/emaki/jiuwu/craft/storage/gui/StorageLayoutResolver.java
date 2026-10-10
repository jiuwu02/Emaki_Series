package emaki.jiuwu.craft.storage.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.event.inventory.InventoryType;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;

public final class StorageLayoutResolver {

    public static final String TEMPLATE_ID = "storage_gui";

    public static final String TYPE_STORAGE_SLOT = "storage_slot";

    public static final String TYPE_DEPOSIT_SLOT = "deposit_slot";

    public static final String TYPE_PAGE_PREV = "page_prev";
    public static final String TYPE_PAGE_INFO = "page_info";
    public static final String TYPE_PAGE_NEXT = "page_next";
    public static final String TYPE_SEARCH = "search";
    public static final String TYPE_SORT = "sort";
    public static final String TYPE_DEPOSIT_ALL = "deposit_all";
    public static final String TYPE_UNLOCK = "unlock";

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 5;
    private static final int ROW_WIDTH = 9;
    private static final String STORAGE_SLOT_KEY = "storage_slot";
    private static final String FUNCTION_SLOTS_KEY = "slots";

    public record Layout(GuiTemplate template, int storageRows, int slotsPerPage) {

        public int totalRows() {
            return storageRows + 1;
        }

        public int functionBase() {
            return storageRows * ROW_WIDTH;
        }
    }

    private final Logger logger;

    public StorageLayoutResolver(Logger logger) {
        this.logger = logger;
    }

    public int clampRows(int configuredRows) {
        if (configuredRows > MAX_ROWS) {
            logger.warning("[config] gui.storage_rows=" + configuredRows
                    + " 超过最大值 " + MAX_ROWS
                    + " (功能行始终占用最后一行)；已限制为 " + MAX_ROWS);
            return MAX_ROWS;
        }
        if (configuredRows < MIN_ROWS) {
            logger.warning("[config] gui.storage_rows=" + configuredRows
                    + " 低于最小值 " + MIN_ROWS + "；已限制为 " + MIN_ROWS);
            return MIN_ROWS;
        }
        return configuredRows;
    }

    public Layout resolve(GuiTemplateLoader loader, int configuredRows) {
        var entry = loader.entry(TEMPLATE_ID);
        if (entry == null || entry.configuration() == null) {
            logger.warning("[gui] GUI 模板 '" + TEMPLATE_ID + "' 未加载");
            return null;
        }
        int storageRows = clampRows(configuredRows);
        int functionBase = storageRows * ROW_WIDTH;
        int totalRows = storageRows + 1;

        var configuration = entry.configuration();
        GuiTemplate parsed = entry.value();
        if (parsed == null) {
            logger.warning("[gui] GUI 模板 '" + TEMPLATE_ID + "' 解析失败");
            return null;
        }

        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        GuiSlot displayPrototype = parsed.slots().get(STORAGE_SLOT_KEY);
        if (displayPrototype == null) {
            logger.warning("[gui] GUI 模板 '" + TEMPLATE_ID + "' 缺少 '"
                    + STORAGE_SLOT_KEY + "' 定义；无法构建展示区");
            return null;
        }
        List<Integer> displaySlots = new ArrayList<>(functionBase);
        for (int slot = 0; slot < functionBase; slot++) {
            displaySlots.add(slot);
        }
        slots.put(STORAGE_SLOT_KEY, withSlots(displayPrototype, TYPE_STORAGE_SLOT, displaySlots));

        Object slotsSection = configuration.get(FUNCTION_SLOTS_KEY);
        Map<Integer, String> claimedOffsets = new LinkedHashMap<>();
        if (slotsSection != null) {
            for (Map.Entry<String, Object> function : ConfigNodes.entries(slotsSection).entrySet()) {
                String key = function.getKey();
                if (STORAGE_SLOT_KEY.equals(key)) {
                    continue;
                }
                Integer offset = readOffset(function.getValue());
                if (offset == null) {
                    logger.warning("[gui] 功能槽位 '" + key
                            + "' 没有有效偏移 (应为 0-" + (ROW_WIDTH - 1) + ")；已跳过");
                    continue;
                }
                String previous = claimedOffsets.putIfAbsent(offset, key);
                if (previous != null) {
                    logger.warning("[gui] 功能槽位 '" + key + "' 复用了已被 '" + previous
                            + "' 占用的偏移 " + offset + "；已跳过");
                    continue;
                }
                GuiSlot prototype = parsed.slots().get(key);
                String type = resolveFunctionType(key, function.getValue());
                slots.put(key, prototype == null
                        ? new GuiSlot(key, List.of(functionBase + offset), type, null, Map.of())
                        : withSlots(prototype, type, List.of(functionBase + offset)));
            }
        }
        if (claimedOffsets.isEmpty()) {
            logger.warning("[gui] GUI 模板 '" + TEMPLATE_ID
                    + "' 未声明任何功能槽位；分页与投入按钮将不可用");
        }

        GuiTemplate rebuilt = new GuiTemplate(parsed.id(), parsed.title(), parsed.titleConfig(),
                InventoryType.CHEST, totalRows, slots);
        return new Layout(rebuilt, storageRows, functionBase);
    }

    private GuiSlot withSlots(GuiSlot prototype, String type, List<Integer> slots) {
        String resolvedType = type == null || type.isBlank() ? prototype.type() : type;
        return new GuiSlot(prototype.key(), slots, resolvedType,
                prototype.itemDefinition(), prototype.sounds());
    }

    private Integer readOffset(Object raw) {
        Object value = ConfigNodes.get(raw, "offset");
        int offset;
        if (value instanceof Number number) {
            offset = number.intValue();
        } else if (value instanceof String text) {
            try {
                offset = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        } else {
            return null;
        }
        return offset >= 0 && offset < ROW_WIDTH ? offset : null;
    }

    private String resolveFunctionType(String key, Object raw) {
        String configured = ConfigNodes.string(raw, "type", null);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return key.toLowerCase(Locale.ROOT);
    }
}
