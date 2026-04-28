package emaki.jiuwu.craft.corelib.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class GuiTemplate {

    public record ResolvedSlot(GuiSlot definition, int inventorySlot, int slotIndex) {

    }

    private final String id;
    private final String title;
    private final Object titleConfig;
    private final int rows;
    private final Map<String, GuiSlot> slots;
    private final Map<Integer, ResolvedSlot> resolvedSlots;

    public GuiTemplate(String id, String title, int rows, Map<String, GuiSlot> slots) {
        this(id, title, null, rows, slots);
    }

    public GuiTemplate(String id, String title, Object titleConfig, int rows, Map<String, GuiSlot> slots) {
        this.id = id;
        this.title = Texts.toStringSafe(title);
        this.titleConfig = ConfigNodes.toPlainData(titleConfig);
        this.rows = rows;
        this.slots = Map.copyOf(slots);
        Map<Integer, ResolvedSlot> resolved = new LinkedHashMap<>();
        for (GuiSlot slot : slots.values()) {
            for (int index = 0; index < slot.slots().size(); index++) {
                resolved.put(slot.slots().get(index), new ResolvedSlot(slot, slot.slots().get(index), index));
            }
        }
        this.resolvedSlots = Map.copyOf(resolved);
    }

    public GuiSlot slot(String key) {
        return slots.get(key);
    }

    public ResolvedSlot resolvedSlotAt(int inventorySlot) {
        return resolvedSlots.get(inventorySlot);
    }

    public List<GuiSlot> slotsByType(String type) {
        if (Texts.isBlank(type)) {
            return List.of();
        }
        List<GuiSlot> result = new ArrayList<>();
        String normalized = Texts.lower(type);
        for (GuiSlot slot : slots.values()) {
            if (slot != null && normalized.equals(Texts.lower(slot.type()))) {
                result.add(slot);
            }
        }
        return result;
    }

    public Map<String, GuiSlot> slots() {
        return slots;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Object titleConfig() {
        return titleConfig;
    }

    public int rows() {
        return rows;
    }
}
