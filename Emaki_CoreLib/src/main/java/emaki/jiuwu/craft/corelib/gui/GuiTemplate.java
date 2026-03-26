package emaki.jiuwu.craft.corelib.gui;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuiTemplate {

    public record ResolvedSlot(GuiSlot definition, int inventorySlot, int slotIndex) {
    }

    private final String id;
    private final String title;
    private final int rows;
    private final Map<String, GuiSlot> slots;
    private final Map<Integer, ResolvedSlot> resolvedSlots;

    public GuiTemplate(String id, String title, int rows, Map<String, GuiSlot> slots) {
        this.id = id;
        this.title = title;
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

    public List<GuiSlot> slotsByAction(String action) {
        if (Texts.isBlank(action)) {
            return List.of();
        }
        List<GuiSlot> result = new ArrayList<>();
        String normalized = Texts.lower(action);
        for (GuiSlot slot : slots.values()) {
            if (slot != null && normalized.equals(Texts.lower(slot.action()))) {
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

    public int rows() {
        return rows;
    }
}
