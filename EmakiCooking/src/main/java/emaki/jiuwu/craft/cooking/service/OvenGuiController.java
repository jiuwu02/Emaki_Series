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

final class OvenGuiController {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final ItemSourceService itemSourceService;
    private final CookingRecipeService recipeService;
    private final OvenStateCodec codec;
    private final Map<UUID, OvenGuiHolder> openSessions = new ConcurrentHashMap<>();

    private OvenRuntimeService runtimeService;

    OvenGuiController(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            ItemSourceService itemSourceService,
            CookingRecipeService recipeService,
            OvenStateCodec codec) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.itemSourceService = itemSourceService;
        this.recipeService = recipeService;
        this.codec = codec;
    }

    void setRuntimeService(OvenRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    boolean openGui(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            return false;
        }
        OvenGuiHolder existingHolder = findOpenSession(coordinates);
        if (existingHolder != null && !player.getUniqueId().equals(existingHolder.viewerId())) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "oven.in_use", Map.of());
            return true;
        }
        OvenGuiHolder holder = new OvenGuiHolder(player.getUniqueId(), coordinates);
        Inventory inventory = createInventory(holder);
        if (inventory == null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "oven.open_failed", Map.of());
            return false;
        }
        holder.attach(inventory);
        loadInventory(coordinates, inventory);
        openSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "oven.opened", Map.of());
        return true;
    }

    Inventory createInventory(OvenGuiHolder holder) {
        String title = MiniMessages.plain(MiniMessages.parse(settingsService.ovenInventoryTitle()));
        return Bukkit.createInventory(holder, settingsService.ovenInventoryRows() * 9, title);
    }

    void loadInventory(StationCoordinates coordinates, Inventory inventory) {
        if (coordinates == null || inventory == null) {
            return;
        }
        inventory.clear();
        OvenState state = runtimeService.loadStateOrEmpty(coordinates);
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
        for (OvenGuiHolder holder : List.copyOf(openSessions.values())) {
            if (holder == null) {
                continue;
            }
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null
                    && viewer.getOpenInventory().getTopInventory().getHolder() == holder) {
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
        for (OvenGuiHolder holder : List.copyOf(openSessions.values())) {
            if (holder == null || !coordinates.equals(holder.coordinates())) {
                continue;
            }
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null
                    && viewer.getOpenInventory().getTopInventory().getHolder() == holder) {
                viewer.closeInventory();
            } else {
                openSessions.remove(holder.viewerId(), holder);
            }
        }
    }

    OvenGuiHolder findOpenSession(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        for (OvenGuiHolder holder : openSessions.values()) {
            if (holder != null && coordinates.equals(holder.coordinates())) {
                return holder;
            }
        }
        return null;
    }

    OvenState snapshotInventoryState(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        OvenState previous = runtimeService.loadStateOrEmpty(coordinates);
        OvenState updated = new OvenState();
        updated.setPlayerContext(playerUuid, playerName);
        updated.setBurningUntilMs(previous.burningUntilMs());
        updated.setHeat(previous.heat());
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
        if (!(event.getInventory().getHolder() instanceof OvenGuiHolder holder)) {
            return;
        }
        openSessions.remove(holder.viewerId(), holder);
        if (holder.suppressSave()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        processExcessItems(player, event.getInventory());
        OvenState state = runtimeService.saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        if (runtimeService.tickProcessor().shouldRemainActive(state, System.currentTimeMillis())) {
            runtimeService.activeStations().add(holder.coordinates());
            runtimeService.ensureTicker();
        } else if (state.isCompletelyEmpty()) {
            runtimeService.activeStations().remove(holder.coordinates());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof OvenGuiHolder)) {
            return;
        }
        Player player = event.getWhoClicked() instanceof Player viewer ? viewer : null;
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        int topSize = topInventory.getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < topSize && !ingredientSlotSet(topInventory).contains(rawSlot)) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= 0 && rawSlot < topSize && rejectsRecipeInput(event.getCursor(), player)) {
            event.setCancelled(true);
            sendInputRejected(player);
            return;
        }
        if (rawSlot >= 0 && rawSlot < topSize && event.getHotbarButton() >= 0 && player != null) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (rejectsRecipeInput(hotbarItem, player)) {
                event.setCancelled(true);
                sendInputRejected(player);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof OvenGuiHolder)) {
            return;
        }
        Player player = event.getWhoClicked() instanceof Player viewer ? viewer : null;
        int topSize = topInventory.getSize();
        Set<Integer> ingredientSlots = ingredientSlotSet(topInventory);
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize && !ingredientSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize) {
                ItemStack newItem = event.getNewItems().get(rawSlot);
                if (rejectsRecipeInput(newItem, player)) {
                    event.setCancelled(true);
                    sendInputRejected(player);
                    return;
                }
            }
        }
    }

    String identifySource(ItemStack itemStack) {
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    List<Integer> ingredientSlots(Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        int size = inventory.getSize();
        List<Integer> configured = settingsService.ovenIngredientSlots();
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
        if (!settingsService.onlyRecipeItems(StationType.OVEN) || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        String source = identifySource(itemStack);
        if (Texts.isBlank(source)) {
            return true;
        }
        RecipeDocument recipe = recipeService.findOvenRecipe(source, player);
        return recipe == null;
    }

    private void sendInputRejected(Player player) {
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "general.input_rejected", Map.of());
    }
}
