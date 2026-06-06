package emaki.jiuwu.craft.corelib.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.event.inventory.InventoryType;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class GuiTemplateParser {

    private GuiTemplateParser() {
    }

    public static GuiTemplate parse(YamlSection section) {
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
                GuiSlot slot = parseSlot(key, slotsSection.get(key));
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
        String configured = section.getString("gui_type", section.getString("inventory_type", "CHEST"));
        if (Texts.isBlank(configured)) {
            return InventoryType.CHEST;
        }
        try {
            return InventoryType.valueOf(configured.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return InventoryType.CHEST;
        }
    }

    private static GuiSlot parseSlot(String key, Object raw) {
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
                parseItemText(raw),
                ItemComponentParser.parse(raw),
                parseSounds(raw)
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

    private static String parseItemText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return Texts.trim(text);
        }
        String item = ConfigNodes.string(raw, "item", null);
        if (Texts.isNotBlank(item)) {
            return item;
        }
        item = parseItemSourceText(ConfigNodes.get(raw, "item_source"));
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
        ItemSource source = ItemSourceUtil.parse(raw);
        return source == null ? null : ItemSourceUtil.toShorthand(source);
    }

    private static String parseItemSourceText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return Texts.trim(text);
        }
        ItemSource source = ItemSourceUtil.parse(raw);
        return source == null ? null : ItemSourceUtil.toShorthand(source);
    }

    private static Map<GuiClickType, SoundParser.SoundDefinition> parseSounds(Object raw) {
        Map<GuiClickType, SoundParser.SoundDefinition> result = new LinkedHashMap<>();
        Object sounds = ConfigNodes.get(raw, "sounds");
        if (sounds != null) {
            SoundParser.SoundDefinition nestedClick = SoundParser.parse(ConfigNodes.get(sounds, "click"));
            SoundParser.SoundDefinition nestedLeft = SoundParser.parse(ConfigNodes.get(sounds, "left_click"));
            SoundParser.SoundDefinition nestedRight = SoundParser.parse(ConfigNodes.get(sounds, "right_click"));
            if (nestedClick != null) {
                result.put(GuiClickType.CLICK, nestedClick);
            }
            if (nestedLeft != null) {
                result.put(GuiClickType.LEFTCLICK, nestedLeft);
            }
            if (nestedRight != null) {
                result.put(GuiClickType.RIGHTCLICK, nestedRight);
            }
        }
        return result;
    }
}
