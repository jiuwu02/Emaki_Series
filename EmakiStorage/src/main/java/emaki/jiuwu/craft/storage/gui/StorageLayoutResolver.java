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

/**
 * Turns {@code gui.storage_rows} into a concrete template.
 *
 * <p>The template file deliberately does not hard-code slot numbers. CoreLib's parser treats
 * {@code slots} as absolute inventory indices, so changing the row count would leave the function
 * row stranded in the middle of the window. Instead the function row declares an in-row
 * {@code offset} (0–8) and this resolver converts it:
 *
 * <pre>
 * storageRows  = clamp(gui.storage_rows, 1, 5)
 * totalRows    = storageRows + 1          // function row always owns the last row
 * functionBase = storageRows * 9
 * display      = 0 .. functionBase - 1
 * function     = functionBase + offset
 * </pre>
 *
 * <p>{@code totalRows} therefore peaks at 6, which already sits inside CoreLib's {@code 1..6}
 * clamp, so no CoreLib row constraint has to change.
 */
public final class StorageLayoutResolver {

    /** Template id loaded from {@code gui/storage_gui.yml}. */
    public static final String TEMPLATE_ID = "storage_gui";

    /** Slot type for the display area. */
    public static final String TYPE_STORAGE_SLOT = "storage_slot";

    /** Slot type for the fixed deposit port. */
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

    /**
     * The resolved layout.
     *
     * @param template     the rebuilt template with absolute slot numbers
     * @param storageRows  the clamped display row count
     * @param slotsPerPage how many entries one page shows
     */
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

    /**
     * Clamps the configured row count, warning rather than silently accepting an illegal value.
     *
     * @param configuredRows the raw {@code gui.storage_rows} value
     * @return the clamped row count in {@code 1..5}
     */
    public int clampRows(int configuredRows) {
        if (configuredRows > MAX_ROWS) {
            logger.warning("[storage] gui.storage_rows=" + configuredRows
                    + " exceeds the maximum of " + MAX_ROWS
                    + " (the function row always occupies the last row); clamped to " + MAX_ROWS + ".");
            return MAX_ROWS;
        }
        if (configuredRows < MIN_ROWS) {
            logger.warning("[storage] gui.storage_rows=" + configuredRows
                    + " is below the minimum of " + MIN_ROWS + "; clamped to " + MIN_ROWS + ".");
            return MIN_ROWS;
        }
        return configuredRows;
    }

    /**
     * Resolves the template for the configured row count.
     *
     * @param loader         the module's template loader, already loaded
     * @param configuredRows the raw {@code gui.storage_rows} value
     * @return the resolved layout, or {@code null} when the template is missing or unusable
     */
    public Layout resolve(GuiTemplateLoader loader, int configuredRows) {
        var entry = loader.entry(TEMPLATE_ID);
        if (entry == null || entry.configuration() == null) {
            logger.warning("[storage] GUI template '" + TEMPLATE_ID + "' was not loaded.");
            return null;
        }
        int storageRows = clampRows(configuredRows);
        int functionBase = storageRows * ROW_WIDTH;
        int totalRows = storageRows + 1;

        var configuration = entry.configuration();
        GuiTemplate parsed = entry.value();
        if (parsed == null) {
            logger.warning("[storage] GUI template '" + TEMPLATE_ID + "' failed to parse.");
            return null;
        }

        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        GuiSlot displayPrototype = parsed.slots().get(STORAGE_SLOT_KEY);
        if (displayPrototype == null) {
            logger.warning("[storage] GUI template '" + TEMPLATE_ID + "' is missing the '"
                    + STORAGE_SLOT_KEY + "' definition; the display area cannot be built.");
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
                    logger.warning("[storage] Function slot '" + key
                            + "' has no valid offset (expected 0-" + (ROW_WIDTH - 1) + "); skipped.");
                    continue;
                }
                String previous = claimedOffsets.putIfAbsent(offset, key);
                if (previous != null) {
                    logger.warning("[storage] Function slot '" + key + "' reuses offset " + offset
                            + " already claimed by '" + previous + "'; skipped.");
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
            logger.warning("[storage] GUI template '" + TEMPLATE_ID
                    + "' declares no function slots; paging and deposit buttons will be unavailable.");
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

    /**
     * Reads the in-row offset of a function entry.
     *
     * <p>Only the explicit {@code offset} key is accepted. The {@code slots} key present in the
     * template is a placeholder that keeps the shared parser happy and is overwritten here, so it
     * must never be mistaken for a real position.
     */
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
