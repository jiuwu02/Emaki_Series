package emaki.jiuwu.craft.corelib.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.event.inventory.InventoryType;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class GuiTemplate {

    public record ResolvedSlot(GuiSlot definition, int inventorySlot, int slotIndex) {

    }

    private final String id;
    private final String title;
    private final Object titleConfig;
    private final InventoryType inventoryType;
    private final int rows;
    private final Map<String, GuiSlot> slots;
    private final Map<Integer, ResolvedSlot> resolvedSlots;

    public GuiTemplate(String id, String title, int rows, Map<String, GuiSlot> slots) {
        this(id, title, null, InventoryType.CHEST, rows, slots);
    }

    public GuiTemplate(String id, String title, Object titleConfig, int rows, Map<String, GuiSlot> slots) {
        this(id, title, titleConfig, InventoryType.CHEST, rows, slots);
    }

    public GuiTemplate(String id, String title, Object titleConfig, InventoryType inventoryType, int rows, Map<String, GuiSlot> slots) {
        this.id = id;
        this.title = Texts.toStringSafe(title);
        this.titleConfig = ConfigNodes.toPlainData(titleConfig);
        this.inventoryType = inventoryType == null ? InventoryType.CHEST : inventoryType;
        this.rows = supportsRows(this.inventoryType) ? Math.max(1, Math.min(6, rows)) : 0;
        this.slots = Map.copyOf(slots == null ? Map.of() : slots);
        Map<Integer, ResolvedSlot> resolved = new LinkedHashMap<>();
        int count = slotCount();
        for (GuiSlot slot : this.slots.values()) {
            for (int index = 0; index < slot.slots().size(); index++) {
                int inventorySlot = slot.slots().get(index);
                if (inventorySlot < 0 || inventorySlot >= count) {
                    continue;
                }
                resolved.put(inventorySlot, new ResolvedSlot(slot, inventorySlot, index));
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

    public InventoryType inventoryType() {
        return inventoryType;
    }

    public boolean isChest() {
        return inventoryType == InventoryType.CHEST;
    }

    public boolean supportsRows() {
        return supportsRows(inventoryType);
    }

    public int rows() {
        return rows;
    }

    public int slotCount() {
        return supportsRows(inventoryType) ? rows * 9 : Math.max(0, inventoryType.getDefaultSize());
    }

    public static boolean supportsRows(InventoryType type) {
        return type == null || type == InventoryType.CHEST;
    }
}
