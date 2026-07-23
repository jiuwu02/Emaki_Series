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
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
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
        debug(player, coordinates, "open requested");
        FermentationBarrelGuiHolder existing = findOpenSession(coordinates);
        if (existing != null && !player.getUniqueId().equals(existing.viewerId())) {
            debug(player, coordinates, "open rejected: reason=in_use viewer=" + existing.viewerId());
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
        debug(player, coordinates, "open completed: size=" + inventory.getSize()
                + " ingredientSlots=" + ingredientSlots(inventory));
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
                ItemSource itemSource = ItemSourceUtil.parse(source);
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
        debug(player, holder.coordinates(), "close received: suppressSave=" + holder.suppressSave());
        if (holder.suppressSave()) {
            debug(player, holder.coordinates(), "close completed: save=skipped");
            return;
        }
        debug(player, holder.coordinates(), "save started: inventorySize=" + event.getInventory().getSize());
        runtimeService.saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        debug(player, holder.coordinates(), "save completed");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player) || !isSessionInventory(player, top)) {
            return;
        }
        StationCoordinates coordinates = viewingCoordinates(player.getUniqueId());
        debug(player, coordinates, clickDetails(event));
        if (event.isShiftClick()) {
            event.setCancelled(true);
            debug(player, coordinates, "click rejected: reason=shift_transfer rawSlot=" + event.getRawSlot());
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize() && !ingredientSlotSet(top).contains(rawSlot)) {
            event.setCancelled(true);
            debug(player, coordinates, "click rejected: reason=non_ingredient_slot rawSlot=" + rawSlot);
            return;
        }
        debug(player, coordinates, "click allowed: rawSlot=" + rawSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player) || !isSessionInventory(player, top)) {
            return;
        }
        StationCoordinates coordinates = viewingCoordinates(player.getUniqueId());
        debug(player, coordinates, "drag evaluated: type=" + event.getType() + " rawSlots=" + event.getRawSlots()
                + " oldCursor=" + describe(event.getOldCursor()));
        Set<Integer> ingredientSlots = ingredientSlotSet(top);
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < top.getSize() && !ingredientSlots.contains(rawSlot)) {
                event.setCancelled(true);
                debug(player, coordinates, "drag rejected: reason=non_ingredient_slot rawSlot=" + rawSlot);
                return;
            }
        }
        debug(player, coordinates, "drag allowed: rawSlots=" + event.getRawSlots());
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
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    private void debug(Player player, StationCoordinates coordinates, String message) {
        GuiDebugSupport.log(plugin, player, "cooking station=" + StationType.FERMENTATION_BARREL
                + " coordinates=" + coordinatesKey(coordinates) + " " + message);
    }

    private String clickDetails(InventoryClickEvent event) {
        return "click evaluated: rawSlot=" + event.getRawSlot() + " action=" + event.getAction()
                + " click=" + event.getClick() + " current=" + describe(event.getCurrentItem())
                + " cursor=" + describe(event.getCursor());
    }

    private String describe(ItemStack itemStack) {
        return GuiDebugSupport.describeItem(itemStack);
    }

    private String coordinatesKey(StationCoordinates coordinates) {
        return coordinates == null ? "unknown" : coordinates.runtimeKey();
    }
}
