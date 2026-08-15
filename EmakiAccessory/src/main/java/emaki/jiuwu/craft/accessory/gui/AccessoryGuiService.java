package emaki.jiuwu.craft.accessory.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.accessory.model.AccessoryPart;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryPartRegistry;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class AccessoryGuiService {

    public static final String TYPE_ACCESSORY_SLOT = "accessory_slot";

    public static final String TYPE_ORPHAN_SLOT = "orphan_slot";

    private static final String TEMPLATE_ID = "accessory_gui";

    public record Layout(GuiTemplate template,
            Map<String, Integer> slotCells,
            Map<Integer, String> cellSlots,
            List<Integer> orphanCells) {

        public Layout {
            slotCells = slotCells == null ? Map.of() : Map.copyOf(slotCells);
            cellSlots = cellSlots == null ? Map.of() : Map.copyOf(cellSlots);
            orphanCells = orphanCells == null ? List.of() : List.copyOf(orphanCells);
        }
    }

    private final GuiService guiService;
    private final MessageService messageService;
    private final List<String> issues = new ArrayList<>();
    private Layout layout;
    private AccessoryPartRegistry registry = AccessoryPartRegistry.empty();
    private String orphanLoreKey = "gui.orphan_hint";

    public AccessoryGuiService(GuiService guiService, MessageService messageService) {
        this.guiService = guiService;
        this.messageService = messageService;
    }

    public List<String> issues() {
        return List.copyOf(issues);
    }

    public Layout layout() {
        return layout;
    }

    public AccessoryPartRegistry registry() {
        return registry;
    }

    public void reconfigure(GuiTemplateLoader templateLoader, AccessoryPartRegistry registry) {
        issues.clear();
        this.registry = registry == null ? AccessoryPartRegistry.empty() : registry;
        GuiTemplate template = templateLoader == null ? null : templateLoader.get(TEMPLATE_ID);
        if (template == null) {
            layout = null;
            warn("accessory.gui_template_missing", Map.of("template", TEMPLATE_ID));
            return;
        }
        Map<String, Integer> slotCells = new LinkedHashMap<>();
        Map<Integer, String> cellSlots = new LinkedHashMap<>();
        List<Integer> orphanCells = new ArrayList<>();
        for (GuiSlot slot : template.slots().values()) {
            if (slot == null || !slot.hasType()) {
                continue;
            }
            String type = Texts.normalizeId(slot.type());
            if (TYPE_ORPHAN_SLOT.equals(type)) {
                orphanCells.addAll(slot.slots());
                continue;
            }
            if (!TYPE_ACCESSORY_SLOT.equals(type)) {
                continue;
            }
            String slotInstanceId = Texts.normalizeId(slot.key());
            List<Integer> cells = slot.slots();
            if (cells.isEmpty()) {
                continue;
            }
            if (cells.size() > 1) {
                warn("accessory.gui_slot_multi_cell", Map.of(
                        "slot", slotInstanceId,
                        "count", Integer.toString(cells.size())
                ));
            }
            Integer cell = cells.get(0);
            if (cellSlots.containsKey(cell)) {
                warn("accessory.gui_cell_conflict", Map.of(
                        "slot", slotInstanceId,
                        "cell", String.valueOf(cell)
                ));
                continue;
            }
            slotCells.put(slotInstanceId, cell);
            cellSlots.put(cell, slotInstanceId);
        }
        for (String slotInstanceId : this.registry.slotInstanceIds()) {
            if (!slotCells.containsKey(slotInstanceId)) {
                warn("accessory.gui_slot_unplaced", Map.of("slot", slotInstanceId));
            }
        }
        layout = new Layout(template, slotCells, cellSlots, orphanCells);
    }

    public void orphanLoreKey(String orphanLoreKey) {
        if (Texts.isNotBlank(orphanLoreKey)) {
            this.orphanLoreKey = orphanLoreKey;
        }
    }

    public GuiSession open(Player viewer, AccessoryGuiHandler handler) {
        if (viewer == null || handler == null || layout == null) {
            return null;
        }
        return guiService.open(new GuiOpenRequest(
                handler.owner(),
                viewer,
                layout.template(),
                Map.of(),
                this::renderSlot,
                handler
        ));
    }

    public void refresh(GuiSession session) {
        if (session != null) {
            session.refresh();
        }
    }

    public String slotInstanceAt(int inventorySlot) {
        Layout active = layout;
        if (active == null) {
            return "";
        }
        return Texts.toStringSafe(active.cellSlots().get(inventorySlot));
    }

    public String orphanKeyAt(PlayerAccessories accessories, int inventorySlot) {
        Layout active = layout;
        if (active == null || accessories == null) {
            return "";
        }
        int position = active.orphanCells().indexOf(inventorySlot);
        if (position < 0) {
            return "";
        }
        List<String> orphans = orphanKeys(accessories);
        return position < orphans.size() ? orphans.get(position) : "";
    }

    public List<String> orphanKeys(PlayerAccessories accessories) {
        if (accessories == null) {
            return List.of();
        }
        List<String> orphans = new ArrayList<>();
        for (String key : accessories.slotKeys()) {
            if (registry.isOrphan(key)) {
                orphans.add(key);
            }
        }
        orphans.sort(String::compareTo);
        return List.copyOf(orphans);
    }

    private ItemStack renderSlot(GuiSession session, GuiTemplate.ResolvedSlot slot) {
        if (session == null || slot == null || slot.definition() == null) {
            return null;
        }
        PlayerAccessories accessories = session.handler() instanceof AccessoryGuiHandler handler
                ? handler.accessories()
                : null;
        String type = Texts.normalizeId(slot.definition().type());
        if (TYPE_ORPHAN_SLOT.equals(type)) {
            return renderOrphan(accessories, slot.inventorySlot());
        }
        if (!TYPE_ACCESSORY_SLOT.equals(type)) {
            return null;
        }
        String slotInstanceId = Texts.normalizeId(slot.definition().key());
        AccessorySlot configured = registry.slot(slotInstanceId);
        if (configured == null) {
            return null;
        }
        ItemStack stored = accessories == null ? null : accessories.itemAt(slotInstanceId);
        if (stored != null && !stored.getType().isAir()) {
            return stored.clone();
        }
        return renderPlaceholder(slot.definition(), configured);
    }

    private ItemStack renderPlaceholder(GuiSlot definition, AccessorySlot configured) {
        return GuiItemBuilder.build(
                definition,
                configured.icon(),
                configured.displayName(),
                List.of(),
                Map.of(),
                guiService.configuredItemService()
        );
    }

    private ItemStack renderOrphan(PlayerAccessories accessories, int inventorySlot) {
        String key = orphanKeyAt(accessories, inventorySlot);
        if (Texts.isBlank(key)) {
            return null;
        }
        ItemStack stored = accessories.itemAt(key);
        if (stored == null || stored.getType().isAir()) {
            return null;
        }
        ItemStack projection = stored.clone();
        String hint = orphanHint(key);
        if (Texts.isNotBlank(hint)) {
            ItemMeta meta = projection.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>(ItemTextBridge.loreLines(meta));
                lore.add(hint);
                ItemTextBridge.setLoreLines(meta, lore);
                projection.setItemMeta(meta);
            }
        }
        return projection;
    }

    private String orphanHint(String key) {
        if (messageService == null) {
            return "";
        }
        return messageService.message(orphanLoreKey, Map.of(
                "slot", key,
                "part", AccessoryPart.partIdOf(key)
        ));
    }

    private void warn(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            issues.add(messageService.message(key, replacements));
            messageService.warning(key, replacements);
            return;
        }
        issues.add(key + " " + replacements);
    }
}
