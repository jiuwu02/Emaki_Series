package emaki.jiuwu.craft.corelib.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryType;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemParser;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.gui.SlotParser;

public final class GuiTemplateParser {

    private static final Map<String, GuiClickType> SOUND_KEYS = Map.ofEntries(
            Map.entry("click", GuiClickType.CLICK),
            Map.entry("left_click", GuiClickType.LEFTCLICK),
            Map.entry("right_click", GuiClickType.RIGHTCLICK),
            Map.entry("shift_left_click", GuiClickType.SHIFT_LEFTCLICK),
            Map.entry("shift_right_click", GuiClickType.SHIFT_RIGHTCLICK),
            Map.entry("middle_click", GuiClickType.MIDDLECLICK),
            Map.entry("double_click", GuiClickType.DOUBLECLICK),
            Map.entry("number_key", GuiClickType.NUMBER_KEY),
            Map.entry("swap_offhand", GuiClickType.SWAP_OFFHAND),
            Map.entry("drop", GuiClickType.DROP),
            Map.entry("control_drop", GuiClickType.CONTROL_DROP)
    );

    private GuiTemplateParser() {
    }

    public static GuiTemplate parse(YamlSection section) {
        return parse(section, null);
    }

    public static GuiTemplate parse(YamlSection section, Consumer<String> issueSink) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        Object titleRaw = section.get("title");
        boolean titleIsTextConfig = titleRaw instanceof Map<?, ?> || titleRaw instanceof YamlSection;
        InventoryType inventoryType = parseInventoryType(section);
        int rows = GuiTemplate.supportsRows(inventoryType) ? Numbers.clamp(Numbers.tryParseInt(section.get("rows"), 3), 1, 6) : 0;
        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        YamlSection slotsSection = section.getSection("slots");
        if (slotsSection != null) {
            for (String key : slotsSection.getKeys(false)) {
                GuiSlot slot = parseSlot(key, slotsSection.get(key), issueSink);
                if (slot != null) {
                    slots.put(key, slot);
                }
            }
        }
        return new GuiTemplate(
                id,
                titleIsTextConfig ? "GUI" : section.getString("title", "GUI"),
                titleIsTextConfig ? titleRaw : null,
                inventoryType,
                rows,
                slots
        );
    }

    private static InventoryType parseInventoryType(YamlSection section) {
        String configured = section.getString("gui_type", "CHEST");
        if (Texts.isBlank(configured)) {
            return InventoryType.CHEST;
        }
        try {
            return InventoryType.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return InventoryType.CHEST;
        }
    }

    private static GuiSlot parseSlot(String key, Object raw, Consumer<String> issueSink) {
        if (raw == null) {
            return null;
        }
        List<Integer> positions = raw instanceof YamlSection || raw instanceof Map<?, ?>
                ? SlotParser.parse(ConfigNodes.get(raw, "slots"))
                : SlotParser.parse(raw);
        if (positions.isEmpty()) {
            return null;
        }
        return new GuiSlot(
                key,
                positions,
                resolveType(key, raw),
                parseItemDefinition(raw),
                parseSounds(raw, issueSink)
        );
    }

    private static String resolveType(String key, Object raw) {
        String configured = ConfigNodes.string(raw, "type", null);
        if (Texts.isNotBlank(configured)) {
            return configured;
        }
        return switch (Texts.lower(key)) {
            case "blueprint_inputs", "target_item", "required_materials", "optional_materials", "recipe_list", "capacity_display", "prev_page", "next_page", "close" ->
                Texts.lower(key);
            case "confirm_button" ->
                "confirm";
            default ->
                null;
        };
    }

    private static ConfiguredItemDefinition parseItemDefinition(Object raw) {
        Object nestedItem = ConfigNodes.get(raw, "item");
        ConfiguredItemParser parser = new ConfiguredItemParser();
        if (nestedItem instanceof Map<?, ?> || nestedItem instanceof YamlSection) {
            return parser.parse(nestedItem);
        }
        String source = parseItemText(raw);
        int amount = Math.max(1, Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1));
        return new ConfiguredItemDefinition(source, amount, Map.of());
    }

    private static String parseItemText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return Texts.trim(text);
        }
        String item = parseItemSourceText(ConfigNodes.get(raw, "item_source"));
        if (Texts.isNotBlank(item)) {
            return item;
        }
        item = parseItemSourceText(ConfigNodes.get(raw, "item_sources"));
        if (Texts.isNotBlank(item)) {
            return item;
        }
        item = ConfigNodes.string(raw, "material", null);
        if (Texts.isNotBlank(item)) {
            return item;
        }
        item = ConfigNodes.string(raw, "item", null);
        if (Texts.isNotBlank(item)) {
            return item;
        }
        ItemSourceRef source = ItemSourceUtil.parse(raw);
        return source == null ? null : ItemSourceUtil.toShorthand(source);
    }

    private static String parseItemSourceText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return Texts.trim(text);
        }
        ItemSourceRef source = ItemSourceUtil.parse(raw);
        return source == null ? null : ItemSourceUtil.toShorthand(source);
    }

    private static Map<GuiClickType, SoundParser.SoundDefinition> parseSounds(Object raw, Consumer<String> issueSink) {
        Map<GuiClickType, SoundParser.SoundDefinition> result = new LinkedHashMap<>();
        Object sounds = ConfigNodes.get(raw, "sounds");
        if (sounds == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(sounds).entrySet()) {
            GuiClickType clickType = SOUND_KEYS.get(Texts.lower(entry.getKey()));
            if (clickType == null) {
                if (issueSink != null) {
                    issueSink.accept("Unknown gui sound key '" + entry.getKey() + "' ignored.");
                }
                continue;
            }
            SoundParser.SoundDefinition definition = SoundParser.parse(entry.getValue());
            if (definition != null) {
                result.put(clickType, definition);
            }
        }
        return result;
    }
}
