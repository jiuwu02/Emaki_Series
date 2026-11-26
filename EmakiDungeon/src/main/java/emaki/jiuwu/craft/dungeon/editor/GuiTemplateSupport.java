package emaki.jiuwu.craft.dungeon.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;

public final class GuiTemplateSupport {

    private final java.util.function.Supplier<GuiTemplateLoader> loader;
    private final ConfiguredItemService items;

    public GuiTemplateSupport(java.util.function.Supplier<GuiTemplateLoader> loader,
            ConfiguredItemService items) {
        this.loader = loader;
        this.items = items;
    }

    public GuiTemplate template(String id) {
        java.util.function.Supplier<GuiTemplateLoader> supplier = loader;
        GuiTemplateLoader resolved = supplier == null ? null : supplier.get();
        return resolved == null || id == null ? null : resolved.get(id);
    }

    public int rows(GuiTemplate template, int fallback) {
        return template == null || !template.supportsRows() ? fallback : template.rows();
    }

    public String title(GuiTemplate template, Map<String, ?> replacements, String fallback) {
        if (template == null) {
            return fallback;
        }
        return GuiTemplateTitle.resolve(template, replacements, fallback);
    }

    public List<Integer> slots(GuiTemplate template, String key) {
        GuiSlot slot = template == null || key == null ? null : template.slot(key);
        return slot == null ? List.of() : slot.slots();
    }

    public int firstSlot(GuiTemplate template, String key, int fallback) {
        List<Integer> slots = slots(template, key);
        return slots.isEmpty() ? fallback : slots.get(0);
    }

    public boolean has(GuiTemplate template, String key) {
        return template != null && key != null && template.slot(key) != null;
    }

    public ItemStack item(GuiTemplate template, String key, Map<String, ?> replacements) {
        GuiSlot slot = template == null || key == null ? null : template.slot(key);
        return slot == null ? null : GuiItemBuilder.build(slot.itemDefinition(), replacements, items);
    }

    public Map<String, GuiSlot> entries(GuiTemplate template) {
        return template == null ? Map.of() : template.slots();
    }

    public static Map<String, Object> args(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return map;
    }

    public static List<Integer> range(int from, int count) {
        List<Integer> slots = new ArrayList<>(Math.max(0, count));
        for (int index = 0; index < count; index++) {
            slots.add(from + index);
        }
        return slots;
    }
}
