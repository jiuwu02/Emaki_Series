package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemPackDefinition;

public final class ItemBrowserGuiService {

    static final String TEMPLATE_PACK_BROWSER = "pack_browser_gui";
    static final String TEMPLATE_ITEM_BROWSER = "item_browser_gui";
    static final String KEY_CURRENT_PAGE = "current_page";
    static final String KEY_PACK_ID = "pack_id";
    static final String TYPE_PACK_ENTRY = "pack_entry";
    static final String TYPE_ITEM_ENTRY = "item_entry";

    private final EmakiItemPlugin plugin;
    private final GuiService guiService;

    public ItemBrowserGuiService(EmakiItemPlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    public boolean openPackBrowser(Player player, int page) {
        if (player == null) {
            return false;
        }
        GuiTemplate template = plugin.guiTemplateLoader().get(TEMPLATE_PACK_BROWSER);
        if (template == null) {
            return false;
        }
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(KEY_CURRENT_PAGE, Math.max(0, page));
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                replacements,
                (guiSession, slot) -> renderPackSlot(guiSession, slot),
                new ItemBrowserGuiHandler(plugin, this)
        ));
        return session != null;
    }

    public boolean openItemBrowser(Player player, String packId, int page) {
        if (player == null) {
            return false;
        }
        GuiTemplate template = plugin.guiTemplateLoader().get(TEMPLATE_ITEM_BROWSER);
        if (template == null) {
            return false;
        }
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(KEY_CURRENT_PAGE, Math.max(0, page));
        replacements.put(KEY_PACK_ID, packId == null ? "" : packId);
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                replacements,
                (guiSession, slot) -> renderItemSlot(guiSession, slot),
                new ItemBrowserGuiHandler(plugin, this)
        ));
        return session != null;
    }

    public List<String> packIds() {
        List<String> ordered = new ArrayList<>(plugin.itemLoader().packIds().values().stream().distinct().toList());
        ordered.sort(Comparator
                .comparingInt((String packId) -> plugin.packLoader().getOrFallback(packId).order())
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(ordered);
    }

    public List<String> itemIds(String packId) {
        String resolved = packId == null ? "" : packId;
        List<String> ids = new ArrayList<>();
        plugin.itemLoader().packIds().forEach((id, owner) -> {
            if (resolved.equals(owner)) {
                ids.add(id);
            }
        });
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    public int itemCount(String packId) {
        return itemIds(packId).size();
    }

    public String packDisplayName(String packId) {
        ItemPackDefinition pack = plugin.packLoader().getOrFallback(packId);
        if (pack.hasDisplayName()) {
            return pack.displayName();
        }
        if (Texts.isBlank(packId)) {
            return plugin.messageService().message("browser.default_pack_name");
        }
        return packId;
    }

    private ItemStack renderPackSlot(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        if (resolved == null || resolved.definition() == null || resolved.definition().type() == null) {
            return null;
        }
        GuiSlot slot = resolved.definition();
        return switch (slot.type()) {
            case TYPE_PACK_ENTRY ->
                renderPackEntry(session, slot, resolved.slotIndex());
            case "page_info" ->
                renderPackPageInfo(session, slot);
            default ->
                null;
        };
    }

    private ItemStack renderPackEntry(GuiSession session, GuiSlot slot, int slotIndex) {
        List<String> packs = packIds();
        int pageSize = Math.max(1, GuiPagination.pageSize(session.template(), TYPE_PACK_ENTRY));
        int index = ItemBrowserGuiHandler.currentPage(session) * pageSize + slotIndex;
        if (index < 0 || index >= packs.size()) {
            return new ItemStack(Material.AIR);
        }
        String packId = packs.get(index);
        ItemPackDefinition pack = plugin.packLoader().getOrFallback(packId);
        Map<String, Object> replacements = packReplacements(packId, pack);
        return GuiItemBuilder.build(
                slot,
                pack.icon(),
                packDisplayName(packId),
                List.of(),
                replacements,
                plugin.coreLib().configuredItemService()
        );
    }

    private Map<String, Object> packReplacements(String packId, ItemPackDefinition pack) {
        int count = itemCount(packId);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("pack", packId);
        replacements.put("pack_name", packDisplayName(packId));
        replacements.put("count", count);
        replacements.put("item_count", count);
        replacements.put("pack_lore", pack.hasLore()
                ? pack.lore()
                : List.of(plugin.messageService().message("browser.pack_lore_fallback", Map.of("count", count)),
                        plugin.messageService().message("browser.pack_click_hint")));
        return replacements;
    }

    private ItemStack renderPackPageInfo(GuiSession session, GuiSlot slot) {
        List<String> packs = packIds();
        int pageSize = Math.max(1, GuiPagination.pageSize(session.template(), TYPE_PACK_ENTRY));
        int totalPages = GuiPagination.totalPages(packs.size(), pageSize);
        int page = ItemBrowserGuiHandler.currentPage(session);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("current_page", page + 1);
        replacements.put("total_pages", totalPages);
        replacements.put("pack_count", packs.size());
        replacements.put("item_count", plugin.itemLoader().all().size());
        return GuiItemBuilder.build(
                slot,
                "minecraft-book",
                plugin.messageService().message("browser.page_info_title", replacements),
                List.of(),
                replacements,
                plugin.coreLib().configuredItemService()
        );
    }

    private ItemStack renderItemSlot(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        if (resolved == null || resolved.definition() == null || resolved.definition().type() == null) {
            return null;
        }
        GuiSlot slot = resolved.definition();
        return switch (slot.type()) {
            case TYPE_ITEM_ENTRY ->
                renderItemEntry(session, resolved.slotIndex());
            case "page_info" ->
                renderItemPageInfo(session, slot);
            default ->
                null;
        };
    }

    private ItemStack renderItemEntry(GuiSession session, int slotIndex) {
        String itemId = itemIdAt(session, slotIndex);
        if (itemId == null) {
            return new ItemStack(Material.AIR);
        }
        EmakiItemDefinition definition = plugin.itemLoader().get(itemId);
        if (definition == null) {
            return new ItemStack(Material.AIR);
        }
        ItemStack icon = plugin.itemFactory().rebuildBase(definition, 1);
        return icon == null ? new ItemStack(Material.AIR) : icon;
    }

    String itemIdAt(GuiSession session, int slotIndex) {
        List<String> ids = itemIds(ItemBrowserGuiHandler.currentPackId(session));
        int pageSize = Math.max(1, GuiPagination.pageSize(session.template(), TYPE_ITEM_ENTRY));
        int index = ItemBrowserGuiHandler.currentPage(session) * pageSize + slotIndex;
        if (index < 0 || index >= ids.size()) {
            return null;
        }
        return ids.get(index);
    }

    private ItemStack renderItemPageInfo(GuiSession session, GuiSlot slot) {
        String packId = ItemBrowserGuiHandler.currentPackId(session);
        List<String> ids = itemIds(packId);
        int pageSize = Math.max(1, GuiPagination.pageSize(session.template(), TYPE_ITEM_ENTRY));
        int totalPages = GuiPagination.totalPages(ids.size(), pageSize);
        int page = ItemBrowserGuiHandler.currentPage(session);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("current_page", page + 1);
        replacements.put("total_pages", totalPages);
        replacements.put("pack", packId);
        replacements.put("pack_name", packDisplayName(packId));
        replacements.put("item_count", ids.size());
        replacements.put("count", ids.size());
        return GuiItemBuilder.build(
                slot,
                "minecraft-book",
                plugin.messageService().message("browser.page_info_title", replacements),
                List.of(),
                replacements,
                plugin.coreLib().configuredItemService()
        );
    }

    int deliverSingle(Player player, String itemId) {
        return deliver(player, itemId, 1);
    }

    int deliverFullStack(Player player, String itemId) {
        ItemStack probe = plugin.itemFactory().create(itemId, 1);
        if (probe == null || probe.getType().isAir()) {
            return 0;
        }
        return deliver(player, itemId, Math.max(1, probe.getMaxStackSize()));
    }

    private int deliver(Player player, String itemId, int amount) {
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(itemId);
        if (definition == null) {
            return 0;
        }
        ItemStack probe = plugin.itemFactory().create(itemId, 1);
        if (probe == null || probe.getType().isAir()) {
            return 0;
        }
        int maxStack = Math.max(1, probe.getMaxStackSize());
        int remaining = Math.max(1, amount);
        int delivered = 0;
        ItemStack reference = null;
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            ItemStack stack = plugin.itemFactory().create(itemId, chunk);
            if (stack == null || stack.getType().isAir()) {
                break;
            }
            int handed = stack.getAmount();
            if (reference == null) {
                reference = stack.clone();
            }
            Map<Integer, ItemStack> leftovers = InventoryItemUtil.addOrDrop(player, stack);
            int unplaced = 0;
            for (ItemStack leftover : leftovers.values()) {
                if (leftover != null) {
                    unplaced += leftover.getAmount();
                }
            }
            delivered += handed;
            remaining -= handed;
            if (!leftovers.isEmpty()) {
                plugin.messageService().send(player, "browser.inventory_full", Map.of("count", unplaced));
                break;
            }
        }
        if (delivered <= 0) {
            return 0;
        }
        plugin.actionService().execute(player, definition, "give", Map.of("amount", delivered), reference);
        plugin.updateService().updatePlayerItems(player, "give");
        plugin.setService().refreshEquippedSets(player, "give");
        plugin.scheduleAttributeEquipmentSync(player);
        return delivered;
    }
}
