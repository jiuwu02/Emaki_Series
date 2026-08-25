package emaki.jiuwu.craft.accessory.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.accessory.model.AccessoryPage;
import emaki.jiuwu.craft.accessory.model.AccessoryPart;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryPageRegistry;
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

    public static final String TYPE_PAGE_ENABLE = "page_enable";

    public static final String TYPE_PAGE_SWITCH = "page_switch";

    public record Layout(GuiTemplate template,
            Map<String, Integer> slotCells,
            Map<Integer, String> cellSlots,
            List<Integer> orphanCells,
            List<Integer> enableCells,
            Map<Integer, String> switchCells) {

        public Layout {
            slotCells = slotCells == null ? Map.of() : Map.copyOf(slotCells);
            cellSlots = cellSlots == null ? Map.of() : Map.copyOf(cellSlots);
            orphanCells = orphanCells == null ? List.of() : List.copyOf(orphanCells);
            enableCells = enableCells == null ? List.of() : List.copyOf(enableCells);
            switchCells = switchCells == null ? Map.of() : Map.copyOf(switchCells);
        }
    }

    private final GuiService guiService;
    private final MessageService messageService;
    private final List<String> issues = new ArrayList<>();
    private final Map<String, Layout> layouts = new LinkedHashMap<>();
    private AccessoryPartRegistry registry = AccessoryPartRegistry.empty();
    private AccessoryPageRegistry pageRegistry = AccessoryPageRegistry.empty();
    private String orphanLoreKey = "gui.orphan_hint";

    public AccessoryGuiService(GuiService guiService, MessageService messageService) {
        this.guiService = guiService;
        this.messageService = messageService;
    }

    public List<String> issues() {
        return List.copyOf(issues);
    }

    public Layout layout(String pageId) {
        return layouts.get(Texts.normalizeId(pageId));
    }

    public AccessoryPartRegistry registry() {
        return registry;
    }

    public AccessoryPageRegistry pageRegistry() {
        return pageRegistry;
    }

    public void reconfigure(GuiTemplateLoader templateLoader,
            AccessoryPartRegistry registry,
            AccessoryPageRegistry pageRegistry) {
        issues.clear();
        layouts.clear();
        this.registry = registry == null ? AccessoryPartRegistry.empty() : registry;
        this.pageRegistry = pageRegistry == null ? AccessoryPageRegistry.empty() : pageRegistry;
        for (String pageId : this.pageRegistry.pageIds()) {
            AccessoryPage page = this.pageRegistry.page(pageId);
            if (page == null) {
                continue;
            }
            Layout built = buildLayout(templateLoader, page);
            if (built != null) {
                layouts.put(pageId, built);
            }
        }
    }

    private Layout buildLayout(GuiTemplateLoader templateLoader, AccessoryPage page) {
        String templateId = page.guiTemplate();
        GuiTemplate template = templateLoader == null ? null : templateLoader.get(templateId);
        if (template == null) {
            warn("accessory.gui_template_missing", Map.of("template", templateId));
            return null;
        }
        Map<String, Integer> slotCells = new LinkedHashMap<>();
        Map<Integer, String> cellSlots = new LinkedHashMap<>();
        List<Integer> orphanCells = new ArrayList<>();
        List<Integer> enableCells = new ArrayList<>();
        Map<Integer, String> switchCells = new LinkedHashMap<>();
        for (GuiSlot slot : template.slots().values()) {
            if (slot == null || !slot.hasType()) {
                continue;
            }
            String type = Texts.normalizeId(slot.type());
            if (TYPE_ORPHAN_SLOT.equals(type)) {
                orphanCells.addAll(slot.slots());
                continue;
            }
            if (TYPE_PAGE_ENABLE.equals(type)) {
                enableCells.addAll(slot.slots());
                continue;
            }
            if (TYPE_PAGE_SWITCH.equals(type)) {
                String target = Texts.normalizeId(slot.key());
                for (Integer cell : slot.slots()) {
                    switchCells.put(cell, target);
                }
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
        for (String slotInstanceId : pageRegistry.slotsOf(page.pageId())) {
            if (!slotCells.containsKey(slotInstanceId)) {
                warn("accessory.gui_slot_unplaced", Map.of(
                        "slot", slotInstanceId,
                        "page", page.pageId()
                ));
            }
        }
        return new Layout(template, slotCells, cellSlots, orphanCells, enableCells, switchCells);
    }

    public void orphanLoreKey(String orphanLoreKey) {
        if (Texts.isNotBlank(orphanLoreKey)) {
            this.orphanLoreKey = orphanLoreKey;
        }
    }

    public GuiSession open(Player viewer, AccessoryGuiHandler handler) {
        if (viewer == null || handler == null) {
            return null;
        }
        Layout active = layout(handler.pageId());
        if (active == null) {
            return null;
        }
        return guiService.open(new GuiOpenRequest(
                handler.owner(),
                viewer,
                active.template(),
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

    public String slotInstanceAt(String pageId, int inventorySlot) {
        Layout active = layout(pageId);
        if (active == null) {
            return "";
        }
        return Texts.toStringSafe(active.cellSlots().get(inventorySlot));
    }

    public String switchTargetAt(String pageId, int inventorySlot) {
        Layout active = layout(pageId);
        if (active == null) {
            return "";
        }
        return Texts.toStringSafe(active.switchCells().get(inventorySlot));
    }

    public String orphanKeyAt(PlayerAccessories accessories, String pageId, int inventorySlot) {
        Layout active = layout(pageId);
        if (active == null || accessories == null) {
            return "";
        }
        int position = active.orphanCells().indexOf(inventorySlot);
        if (position < 0) {
            return "";
        }
        List<String> orphans = orphanKeys(accessories, pageId);
        return position < orphans.size() ? orphans.get(position) : "";
    }

    public List<String> orphanKeys(PlayerAccessories accessories, String pageId) {
        if (accessories == null) {
            return List.of();
        }
        String page = Texts.normalizeId(pageId);
        List<String> orphans = new ArrayList<>();
        for (String key : accessories.slotKeys(page)) {
            if (!pageRegistry.declaresSlot(page, key)) {
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
        AccessoryGuiHandler handler = session.handler() instanceof AccessoryGuiHandler active
                ? active
                : null;
        if (handler == null) {
            return null;
        }
        PlayerAccessories accessories = handler.accessories();
        String pageId = handler.pageId();
        String type = Texts.normalizeId(slot.definition().type());
        if (TYPE_ORPHAN_SLOT.equals(type)) {
            return renderOrphan(accessories, pageId, slot.inventorySlot());
        }
        if (TYPE_PAGE_ENABLE.equals(type)) {
            return renderPageEnable(slot.definition(), accessories, pageId);
        }
        if (TYPE_PAGE_SWITCH.equals(type)) {
            return renderPageSwitch(slot.definition(), pageId, slot.inventorySlot());
        }
        if (!TYPE_ACCESSORY_SLOT.equals(type)) {
            return null;
        }
        String slotInstanceId = Texts.normalizeId(slot.definition().key());
        if (!pageRegistry.declaresSlot(pageId, slotInstanceId)) {
            return null;
        }
        AccessorySlot configured = registry.slot(slotInstanceId);
        if (configured == null) {
            return null;
        }
        ItemStack stored = accessories == null ? null : accessories.itemAt(pageId, slotInstanceId);
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

    private ItemStack renderPageEnable(GuiSlot definition, PlayerAccessories accessories, String pageId) {
        AccessoryPage page = pageRegistry.page(pageId);
        if (page == null) {
            return null;
        }
        boolean active = accessories != null
                && Texts.normalizeId(accessories.enabledPage()).equals(page.pageId());
        String loreKey = active ? "gui.page_enabled_lore" : "gui.page_enable_lore";
        return GuiItemBuilder.build(
                definition,
                "",
                page.label(),
                messageLines(loreKey, Map.of("page", page.label())),
                Map.of(),
                guiService.configuredItemService()
        );
    }

    private ItemStack renderPageSwitch(GuiSlot definition, String currentPageId, int inventorySlot) {
        String target = switchTargetAt(currentPageId, inventorySlot);
        AccessoryPage page = pageRegistry.page(target);
        if (page == null) {
            return null;
        }
        return GuiItemBuilder.build(
                definition,
                "",
                page.label(),
                messageLines("gui.page_switch_lore", Map.of("page", page.label())),
                Map.of(),
                guiService.configuredItemService()
        );
    }

    private List<String> messageLines(String key, Map<String, ?> replacements) {
        if (messageService == null) {
            return List.of();
        }
        String rendered = messageService.message(key, replacements);
        return Texts.isBlank(rendered) ? List.of() : List.of(rendered);
    }

    private ItemStack renderOrphan(PlayerAccessories accessories, String pageId, int inventorySlot) {
        String key = orphanKeyAt(accessories, pageId, inventorySlot);
        if (Texts.isBlank(key)) {
            return null;
        }
        ItemStack stored = accessories.itemAt(pageId, key);
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
