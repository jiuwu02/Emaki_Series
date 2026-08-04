package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.gui.GuiDebugSupport;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class FermentationBarrelGuiController {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final ItemSourceService itemSourceService;
    private final FermentationBarrelStateCodec codec;
    private final Map<UUID, FermentationBarrelGuiHolder> openSessions = new ConcurrentHashMap<>();
    private FermentationBarrelRuntimeService runtimeService;

    FermentationBarrelGuiController(EmakiCookingPlugin plugin, MessageService messageService, CookingSettingsService settingsService,
            ItemSourceService itemSourceService, FermentationBarrelStateCodec codec) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.itemSourceService = itemSourceService;
        this.codec = codec;
    }

    void setRuntimeService(FermentationBarrelRuntimeService runtimeService) { this.runtimeService = runtimeService; }
    boolean hasOpenSession(StationCoordinates coordinates) { return findOpenSession(coordinates) != null; }

    boolean openGui(Player player, StationCoordinates coordinates) {
        debug(player, coordinates, "gui.fermentation_barrel.open_requested");
        FermentationBarrelGuiHolder existing = findOpenSession(coordinates);
        if (existing != null && !player.getUniqueId().equals(existing.viewerId())) {
            debug(player, coordinates, "gui.fermentation_barrel.open_rejected_in_use", GuiDebugSupport.replacements(
                    "viewer", existing.viewerId()
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "fermentation_barrel.in_use", Map.of());
            return true;
        }
        FermentationBarrelGuiHolder holder = new FermentationBarrelGuiHolder(player.getUniqueId(), coordinates);
        Inventory inventory = Bukkit.createInventory(holder, settingsService.fermentationBarrelInventoryRows() * 9,
                MiniMessages.plain(MiniMessages.parse(settingsService.fermentationBarrelInventoryTitle())));
        holder.attach(inventory);
        loadInventory(coordinates, inventory);
        openSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        debug(player, coordinates, "gui.fermentation_barrel.open_completed", GuiDebugSupport.replacements(
                "inventory_size", inventory.getSize(),
                "ingredient_slots", ingredientSlots(inventory)
        ));
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "fermentation_barrel.opened", Map.of());
        return true;
    }

    void loadInventory(StationCoordinates coordinates, Inventory inventory) {
        inventory.clear();
        FermentationBarrelState state = runtimeService.loadStateOrEmpty(coordinates);
        for (int slot : ingredientSlots(inventory)) {
            String source = state.slotSources().get(slot);
            if (Texts.isBlank(source)) {
                continue;
            }
            ItemStack item = codec.deserializeItem(state.slotItemData(slot));
            if (item == null || item.getType().isAir()) {
                ItemSourceRef itemSource = ItemSourceUtil.parse(source);
                item = itemSource == null ? null : itemSourceService.createItem(itemSource, state.slotAmounts().getOrDefault(slot, 1));
            }
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(slot, item);
            }
        }
    }

    FermentationBarrelState snapshotInventoryState(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        FermentationBarrelState previous = runtimeService.loadStateOrEmpty(coordinates);
        FermentationBarrelState updated = new FermentationBarrelState();
        updated.setPlayerContext(playerUuid, playerName);
        updated.setStartedAtMs(previous.startedAtMs());
        updated.setFinishAtMs(previous.finishAtMs());
        updated.setFermenting(previous.fermenting());
        updated.setCompleted(previous.completed());
        updated.setActiveRecipeId(previous.activeRecipeId());
        if (inventory == null) {
            return updated;
        }
        Player player = playerUuid == null ? null : Bukkit.getPlayer(playerUuid);
        Set<Integer> ingredientSlots = ingredientSlotSet(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!ingredientSlots.contains(slot)) {
                if (player != null) {
                    inventory.clear(slot);
                    InventoryItemUtil.giveOrDrop(player, item);
                }
                continue;
            }
            String source = identifySource(item);
            if (Texts.isBlank(source)) {
                if (player != null) {
                    inventory.clear(slot);
                    InventoryItemUtil.giveOrDrop(player, item);
                }
                continue;
            }
            updated.setSlot(slot, source, codec.serializeItem(item), item.getAmount());
        }
        return updated;
    }

    void closeAllOpenInventories(boolean suppressSave) {
        for (FermentationBarrelGuiHolder holder : List.copyOf(openSessions.values())) {
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null && viewer.getOpenInventory().getTopInventory() == holder.getInventory()) {
                viewer.closeInventory();
            }
        }
        if (suppressSave) {
            openSessions.clear();
        }
    }

    void closeOpenInventories(StationCoordinates coordinates, boolean suppressSave) {
        for (FermentationBarrelGuiHolder holder : List.copyOf(openSessions.values())) {
            if (!coordinates.equals(holder.coordinates())) {
                continue;
            }
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null && viewer.getOpenInventory().getTopInventory() == holder.getInventory()) {
                viewer.closeInventory();
            } else {
                openSessions.remove(holder.viewerId(), holder);
            }
        }
    }

    FermentationBarrelGuiHolder findOpenSession(StationCoordinates coordinates) {
        for (FermentationBarrelGuiHolder holder : openSessions.values()) {
            if (holder != null && coordinates.equals(holder.coordinates())) {
                return holder;
            }
        }
        return null;
    }

    StationCoordinates viewingCoordinates(UUID viewerId) {
        if (viewerId == null) {
            return null;
        }
        FermentationBarrelGuiHolder holder = openSessions.get(viewerId);
        return holder == null ? null : holder.coordinates();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        FermentationBarrelGuiHolder holder = openSessions.get(player.getUniqueId());
        if (holder == null || event.getInventory() != holder.getInventory()) {
            return;
        }
        openSessions.remove(holder.viewerId(), holder);
        debug(player, holder.coordinates(), "gui.fermentation_barrel.close_received", GuiDebugSupport.replacements(
                "suppress_save", holder.suppressSave()
        ));
        if (holder.suppressSave()) {
            debug(player, holder.coordinates(), "gui.fermentation_barrel.close_completed_save_skipped");
            return;
        }
        debug(player, holder.coordinates(), "gui.fermentation_barrel.save_started", GuiDebugSupport.replacements(
                "inventory_size", event.getInventory().getSize()
        ));
        runtimeService.saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        debug(player, holder.coordinates(), "gui.fermentation_barrel.save_completed");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player) || !isSessionInventory(player, top)) {
            return;
        }
        StationCoordinates coordinates = viewingCoordinates(player.getUniqueId());
        debug(player, coordinates, "gui.fermentation_barrel.click_evaluated", clickFields(event));
        if (event.isShiftClick()) {
            event.setCancelled(true);
            debug(player, coordinates, "gui.fermentation_barrel.click_rejected_shift_transfer", GuiDebugSupport.replacements(
                    "raw_slot", event.getRawSlot()
            ));
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize() && !ingredientSlotSet(top).contains(rawSlot)) {
            event.setCancelled(true);
            debug(player, coordinates, "gui.fermentation_barrel.click_rejected_non_ingredient_slot", GuiDebugSupport.replacements(
                    "raw_slot", rawSlot
            ));
            return;
        }
        debug(player, coordinates, "gui.fermentation_barrel.click_allowed", GuiDebugSupport.replacements(
                "raw_slot", rawSlot
        ));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player) || !isSessionInventory(player, top)) {
            return;
        }
        StationCoordinates coordinates = viewingCoordinates(player.getUniqueId());
        debug(player, coordinates, "gui.fermentation_barrel.drag_evaluated", GuiDebugSupport.replacements(
                "drag_type", event.getType(),
                "raw_slots", event.getRawSlots(),
                "old_cursor_type", GuiDebugSupport.itemType(event.getOldCursor()),
                "old_cursor_amount", GuiDebugSupport.itemAmount(event.getOldCursor())
        ));
        Set<Integer> ingredientSlots = ingredientSlotSet(top);
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < top.getSize() && !ingredientSlots.contains(rawSlot)) {
                event.setCancelled(true);
                debug(player, coordinates, "gui.fermentation_barrel.drag_rejected_non_ingredient_slot", GuiDebugSupport.replacements(
                        "raw_slot", rawSlot
                ));
                return;
            }
        }
        debug(player, coordinates, "gui.fermentation_barrel.drag_allowed", GuiDebugSupport.replacements(
                "raw_slots", event.getRawSlots()
        ));
    }

    List<Integer> ingredientSlots(Inventory inventory) {
        int size = inventory.getSize();
        List<Integer> slots = new ArrayList<>();
        for (Integer slot : settingsService.fermentationBarrelIngredientSlots()) {
            if (slot != null && slot >= 0 && slot < size) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    Set<Integer> ingredientSlotSet(Inventory inventory) { return Set.copyOf(ingredientSlots(inventory)); }

    private boolean isSessionInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return false;
        }
        FermentationBarrelGuiHolder holder = openSessions.get(player.getUniqueId());
        return holder != null && inventory == holder.getInventory();
    }

    String identifySource(ItemStack itemStack) {
        ItemSourceRef source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    private void debug(Player player, StationCoordinates coordinates, String langKey) {
        debug(player, coordinates, langKey, Map.of());
    }

    private void debug(Player player, StationCoordinates coordinates, String langKey, Map<String, ?> fields) {
        Map<String, Object> replacements = GuiDebugSupport.replacements(
                "station", StationType.FERMENTATION_BARREL,
                "coordinates", coordinates == null ? "" : coordinates.runtimeKey()
        );
        replacements.putAll(fields);
        GuiDebugSupport.log(plugin, player, langKey, replacements);
    }

    private Map<String, Object> clickFields(InventoryClickEvent event) {
        return GuiDebugSupport.replacements(
                "raw_slot", event.getRawSlot(),
                "action", event.getAction(),
                "click", event.getClick(),
                "current_item_type", GuiDebugSupport.itemType(event.getCurrentItem()),
                "current_item_amount", GuiDebugSupport.itemAmount(event.getCurrentItem()),
                "cursor_item_type", GuiDebugSupport.itemType(event.getCursor()),
                "cursor_item_amount", GuiDebugSupport.itemAmount(event.getCursor())
        );
    }
}
