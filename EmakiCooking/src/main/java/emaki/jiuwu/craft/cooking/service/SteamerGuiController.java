package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
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
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class SteamerGuiController implements Listener {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final ItemSourceService itemSourceService;
    private final CookingRecipeService recipeService;
    private final SteamerStateCodec codec;
    private final Map<UUID, SteamerGuiHolder> openSessions = new ConcurrentHashMap<>();

    private SteamerRuntimeService runtimeService;

    SteamerGuiController(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            ItemSourceService itemSourceService,
            CookingRecipeService recipeService,
            SteamerStateCodec codec) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.itemSourceService = itemSourceService;
        this.recipeService = recipeService;
        this.codec = codec;
    }

    void setRuntimeService(SteamerRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    Map<UUID, SteamerGuiHolder> openSessions() {
        return openSessions;
    }

    boolean openGui(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            if (player != null) {
                debug(player, coordinates, "gui.steamer.open_rejected_missing_coordinates");
            }
            return false;
        }
        debug(player, coordinates, "gui.steamer.open_requested");
        SteamerGuiHolder existingHolder = findOpenSession(coordinates);
        if (existingHolder != null && !player.getUniqueId().equals(existingHolder.viewerId())) {
            debug(player, coordinates, "gui.steamer.open_rejected_in_use", GuiDebugSupport.replacements(
                    "viewer", existingHolder.viewerId()
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.in_use", Map.of());
            return true;
        }
        SteamerGuiHolder holder = new SteamerGuiHolder(player.getUniqueId(), coordinates);
        Inventory inventory = createInventory(holder);
        if (inventory == null) {
            debug(player, coordinates, "gui.steamer.open_failed_inventory_creation");
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.open_failed", Map.of());
            return false;
        }
        holder.attach(inventory);
        loadInventory(coordinates, inventory);
        openSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        debug(player, coordinates, "gui.steamer.open_completed", GuiDebugSupport.replacements(
                "inventory_size", inventory.getSize(),
                "ingredient_slots", ingredientSlots(inventory)
        ));
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.opened", Map.of());
        return true;
    }

    Inventory createInventory(SteamerGuiHolder holder) {
        String title = MiniMessages.plain(MiniMessages.parse(settingsService.steamerInventoryTitle()));
        return Bukkit.createInventory(holder, settingsService.steamerInventoryRows() * 9, title);
    }

    void loadInventory(StationCoordinates coordinates, Inventory inventory) {
        if (coordinates == null || inventory == null) {
            return;
        }
        inventory.clear();
        SteamerState state = runtimeService.loadStateOrEmpty(coordinates);
        for (int slot : ingredientSlots(inventory)) {
            String source = state.slotSources().get(slot);
            if (Texts.isBlank(source)) {
                continue;
            }
            ItemStack itemStack = codec.deserializeItem(state.slotItemData(slot));
            if (itemStack == null || itemStack.getType().isAir()) {
                ItemSource itemSource = ItemSourceUtil.parse(source);
                itemStack = itemSource == null ? null : itemSourceService.createItem(itemSource, 1);
            }
            if (itemStack != null && !itemStack.getType().isAir()) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    void processExcessItems(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        Set<Integer> ingredientSlots = ingredientSlotSet(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            if (!ingredientSlots.contains(slot)) {
                inventory.clear(slot);
                InventoryItemUtil.giveOrDrop(player, itemStack);
                continue;
            }
            if (rejectsRecipeInput(itemStack, player)) {
                inventory.clear(slot);
                InventoryItemUtil.giveOrDrop(player, itemStack);
                sendInputRejected(player);
                continue;
            }
            if (itemStack.getAmount() <= 1) {
                continue;
            }
            ItemStack excess = itemStack.clone();
            excess.setAmount(itemStack.getAmount() - 1);
            itemStack.setAmount(1);
            inventory.setItem(slot, itemStack);
            InventoryItemUtil.giveOrDrop(player, excess);
        }
    }

    void closeAllOpenInventories(boolean suppressSave) {
        for (SteamerGuiHolder holder : List.copyOf(openSessions.values())) {
            if (holder == null) {
                continue;
            }
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null
                    && viewer.getOpenInventory().getTopInventory() == holder.getInventory()) {
                viewer.closeInventory();
            }
        }
        if (suppressSave) {
            openSessions.clear();
        }
    }

    void closeOpenInventories(StationCoordinates coordinates, boolean suppressSave) {
        if (coordinates == null) {
            return;
        }
        for (SteamerGuiHolder holder : List.copyOf(openSessions.values())) {
            if (holder == null || !coordinates.equals(holder.coordinates())) {
                continue;
            }
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null
                    && viewer.getOpenInventory().getTopInventory() == holder.getInventory()) {
                viewer.closeInventory();
            } else {
                openSessions.remove(holder.viewerId(), holder);
            }
        }
    }

    SteamerGuiHolder findOpenSession(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        for (SteamerGuiHolder holder : openSessions.values()) {
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
        SteamerGuiHolder holder = openSessions.get(viewerId);
        return holder == null ? null : holder.coordinates();
    }

    SteamerState snapshotInventoryState(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        SteamerState previous = runtimeService.loadStateOrEmpty(coordinates);
        SteamerState updated = new SteamerState();
        updated.setPlayerContext(playerUuid, playerName);
        updated.setBurningUntilMs(previous.burningUntilMs());
        updated.setMoisture(previous.moisture());
        updated.setSteam(previous.steam());
        updated.clearSlots();
        if (inventory == null) {
            return updated;
        }
        Player player = playerUuid == null ? null : Bukkit.getPlayer(playerUuid);
        Set<Integer> ingredientSlots = ingredientSlotSet(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            if (!ingredientSlots.contains(slot)) {
                if (player != null) {
                    inventory.clear(slot);
                    InventoryItemUtil.giveOrDrop(player, itemStack);
                }
                continue;
            }
            String source = identifySource(itemStack);
            if (Texts.isBlank(source)) {
                if (player != null) {
                    inventory.clear(slot);
                    InventoryItemUtil.giveOrDrop(player, itemStack);
                }
                continue;
            }
            if (rejectsRecipeInput(itemStack, player)) {
                if (player != null) {
                    inventory.clear(slot);
                    InventoryItemUtil.giveOrDrop(player, itemStack);
                    sendInputRejected(player);
                }
                continue;
            }
            updated.setSlotSource(slot, source);
            updated.setSlotItem(slot, codec.serializeItem(itemStack));
            if (source.equals(previous.slotSources().get(slot))) {
                updated.setProgress(slot, previous.progressAt(slot));
            } else {
                updated.setProgress(slot, 0);
            }
        }
        return updated;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        SteamerGuiHolder holder = openSessions.get(player.getUniqueId());
        if (holder == null || event.getInventory() != holder.getInventory()) {
            return;
        }
        openSessions.remove(holder.viewerId(), holder);
        debug(player, holder.coordinates(), "gui.steamer.close_received", GuiDebugSupport.replacements(
                "suppress_save", holder.suppressSave()
        ));
        if (holder.suppressSave()) {
            debug(player, holder.coordinates(), "gui.steamer.close_completed_save_skipped");
            return;
        }
        processExcessItems(player, event.getInventory());
        debug(player, holder.coordinates(), "gui.steamer.save_started", GuiDebugSupport.replacements(
                "inventory_size", event.getInventory().getSize()
        ));
        SteamerState state = runtimeService.saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        if (runtimeService.tickProcessor().shouldRemainActive(state, System.currentTimeMillis())) {
            runtimeService.activeStations().add(holder.coordinates());
            runtimeService.ensureTicker();
        } else if (state.isCompletelyEmpty()) {
            runtimeService.activeStations().remove(holder.coordinates());
        }
        debug(player, holder.coordinates(), "gui.steamer.save_completed", GuiDebugSupport.replacements(
                "empty", state.isCompletelyEmpty(),
                "stored_slots", state.slotSources().size()
        ));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player) || !isSessionInventory(player, topInventory)) {
            return;
        }
        StationCoordinates coordinates = viewingCoordinates(player.getUniqueId());
        debug(player, coordinates, "gui.steamer.click_evaluated", clickFields(event));
        if (event.isShiftClick()) {
            event.setCancelled(true);
            debug(player, coordinates, "gui.steamer.click_rejected_shift_transfer", GuiDebugSupport.replacements(
                    "raw_slot", event.getRawSlot()
            ));
            return;
        }
        int topSize = topInventory.getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < topSize && !ingredientSlotSet(topInventory).contains(rawSlot)) {
            event.setCancelled(true);
            debug(player, coordinates, "gui.steamer.click_rejected_non_ingredient_slot", GuiDebugSupport.replacements(
                    "raw_slot", rawSlot
            ));
            return;
        }
        if (rawSlot >= 0 && rawSlot < topSize && rejectsRecipeInput(event.getCursor(), player)) {
            event.setCancelled(true);
            debug(player, coordinates, "gui.steamer.click_rejected_recipe_validation", GuiDebugSupport.replacements(
                    "raw_slot", rawSlot,
                    "item_type", GuiDebugSupport.itemType(event.getCursor()),
                    "item_amount", GuiDebugSupport.itemAmount(event.getCursor())
            ));
            sendInputRejected(player);
            return;
        }
        if (rawSlot >= 0 && rawSlot < topSize && event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (rejectsRecipeInput(hotbarItem, player)) {
                event.setCancelled(true);
                debug(player, coordinates, "gui.steamer.click_rejected_hotbar_recipe_validation", GuiDebugSupport.replacements(
                        "raw_slot", rawSlot,
                        "hotbar", event.getHotbarButton(),
                        "item_type", GuiDebugSupport.itemType(hotbarItem),
                        "item_amount", GuiDebugSupport.itemAmount(hotbarItem)
                ));
                sendInputRejected(player);
                return;
            }
        }
        debug(player, coordinates, "gui.steamer.click_allowed", GuiDebugSupport.replacements(
                "raw_slot", rawSlot
        ));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player) || !isSessionInventory(player, topInventory)) {
            return;
        }
        StationCoordinates coordinates = viewingCoordinates(player.getUniqueId());
        debug(player, coordinates, "gui.steamer.drag_evaluated", GuiDebugSupport.replacements(
                "drag_type", event.getType(),
                "raw_slots", event.getRawSlots(),
                "old_cursor_type", GuiDebugSupport.itemType(event.getOldCursor()),
                "old_cursor_amount", GuiDebugSupport.itemAmount(event.getOldCursor())
        ));
        int topSize = topInventory.getSize();
        Set<Integer> ingredientSlots = ingredientSlotSet(topInventory);
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize && !ingredientSlots.contains(rawSlot)) {
                event.setCancelled(true);
                debug(player, coordinates, "gui.steamer.drag_rejected_non_ingredient_slot", GuiDebugSupport.replacements(
                        "raw_slot", rawSlot
                ));
                return;
            }
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize) {
                ItemStack newItem = event.getNewItems().get(rawSlot);
                if (rejectsRecipeInput(newItem, player)) {
                    event.setCancelled(true);
                    debug(player, coordinates, "gui.steamer.drag_rejected_recipe_validation", GuiDebugSupport.replacements(
                            "raw_slot", rawSlot,
                            "item_type", GuiDebugSupport.itemType(newItem),
                            "item_amount", GuiDebugSupport.itemAmount(newItem)
                    ));
                    sendInputRejected(player);
                    return;
                }
            }
        }
        debug(player, coordinates, "gui.steamer.drag_allowed", GuiDebugSupport.replacements(
                "raw_slots", event.getRawSlots()
        ));
    }

    String identifySource(ItemStack itemStack) {
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    private boolean isSessionInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return false;
        }
        SteamerGuiHolder holder = openSessions.get(player.getUniqueId());
        return holder != null && inventory == holder.getInventory();
    }

    List<Integer> ingredientSlots(Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        int size = inventory.getSize();
        List<Integer> configured = settingsService.steamerIngredientSlots();
        List<Integer> slots = new ArrayList<>();
        for (Integer slot : configured) {
            if (slot != null && slot >= 0 && slot < size) {
                slots.add(slot);
            }
        }
        return slots.isEmpty() ? List.of() : List.copyOf(slots);
    }

    Set<Integer> ingredientSlotSet(Inventory inventory) {
        return Set.copyOf(ingredientSlots(inventory));
    }

    private boolean rejectsRecipeInput(ItemStack itemStack, Player player) {
        if (!settingsService.onlyRecipeItems(StationType.STEAMER) || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        String source = identifySource(itemStack);
        if (Texts.isBlank(source)) {
            return true;
        }
        RecipeDocument recipe = recipeService.findSteamerRecipe(source, player);
        return recipe == null;
    }

    private void sendInputRejected(Player player) {
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "general.input_rejected", Map.of());
    }

    private void debug(Player player, StationCoordinates coordinates, String langKey) {
        debug(player, coordinates, langKey, Map.of());
    }

    private void debug(Player player, StationCoordinates coordinates, String langKey, Map<String, ?> fields) {
        Map<String, Object> replacements = GuiDebugSupport.replacements(
                "station", StationType.STEAMER,
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
