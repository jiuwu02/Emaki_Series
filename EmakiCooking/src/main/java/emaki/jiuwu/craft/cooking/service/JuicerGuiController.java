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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class JuicerGuiController {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final ItemSourceService itemSourceService;
    private final CookingRecipeService recipeService;
    private final JuicerStateCodec codec;
    private final Map<UUID, JuicerGuiHolder> openSessions = new ConcurrentHashMap<>();

    private JuicerRuntimeService runtimeService;

    JuicerGuiController(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            ItemSourceService itemSourceService,
            CookingRecipeService recipeService,
            JuicerStateCodec codec) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.itemSourceService = itemSourceService;
        this.recipeService = recipeService;
        this.codec = codec;
    }

    void setRuntimeService(JuicerRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    boolean openGui(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            if (player != null) {
                debug(player, coordinates, "open rejected: reason=missing_coordinates");
            }
            return false;
        }
        debug(player, coordinates, "open requested");
        JuicerGuiHolder existingHolder = findOpenSession(coordinates);
        if (existingHolder != null && !player.getUniqueId().equals(existingHolder.viewerId())) {
            debug(player, coordinates, "open rejected: reason=in_use viewer=" + existingHolder.viewerId());
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.in_use", Map.of());
            return true;
        }
        JuicerGuiHolder holder = new JuicerGuiHolder(player.getUniqueId(), coordinates);
        Inventory inventory = Bukkit.createInventory(holder, settingsService.juicerInventoryRows() * 9,
                MiniMessages.plain(MiniMessages.parse(settingsService.juicerInventoryTitle())));
        holder.attach(inventory);
        loadInventory(coordinates, inventory);
        openSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        debug(player, coordinates, "open completed: size=" + inventory.getSize()
                + " ingredientSlots=" + ingredientSlots(inventory));
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.opened", Map.of());
        return true;
    }

    void loadInventory(StationCoordinates coordinates, Inventory inventory) {
        if (coordinates == null || inventory == null) {
            return;
        }
        inventory.clear();
        JuicerState state = runtimeService.loadStateOrEmpty(coordinates);
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

    JuicerState snapshotInventoryState(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        JuicerState previous = runtimeService.loadStateOrEmpty(coordinates);
        JuicerState updated = new JuicerState();
        updated.setPlayerContext(playerUuid, playerName);
        updated.setFluid(previous.fluidId(), previous.fluidDisplayName(), previous.fluidAmountMl());
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
            if (Texts.isBlank(source) || rejectsRecipeInput(coordinates, itemStack, player)) {
                if (player != null) {
                    inventory.clear(slot);
                    InventoryItemUtil.giveOrDrop(player, itemStack);
                    sendInputRejected(player);
                }
                continue;
            }
            if (itemStack.getAmount() > 1) {
                ItemStack excess = itemStack.clone();
                excess.setAmount(itemStack.getAmount() - 1);
                itemStack.setAmount(1);
                inventory.setItem(slot, itemStack);
                if (player != null) {
                    InventoryItemUtil.giveOrDrop(player, excess);
                }
            }
            updated.setSlotSource(slot, source);
            updated.setSlotItem(slot, codec.serializeItem(itemStack));
            if (source.equals(previous.slotSources().get(slot))) {
                updated.setProgress(slot, previous.progressAt(slot));
            }
        }
        return updated;
    }

    void closeAllOpenInventories(boolean suppressSave) {
        for (JuicerGuiHolder holder : List.copyOf(openSessions.values())) {
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
        for (JuicerGuiHolder holder : List.copyOf(openSessions.values())) {
            if (!coordinates.equals(holder.coordinates())) {
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

    JuicerGuiHolder findOpenSession(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        for (JuicerGuiHolder holder : openSessions.values()) {
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
        JuicerGuiHolder holder = openSessions.get(viewerId);
        return holder == null ? null : holder.coordinates();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        JuicerGuiHolder holder = openSessions.get(player.getUniqueId());
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
        JuicerState state = runtimeService.saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        if (state.isCompletelyEmpty()) {
            runtimeService.removeState(holder.coordinates(), true);
        }
        debug(player, holder.coordinates(), "save completed: empty=" + state.isCompletelyEmpty()
                + " storedSlots=" + state.slotSources().size() + " fluid=" + Texts.toStringSafe(state.fluidId()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        JuicerGuiHolder holder = openSessions.get(player.getUniqueId());
        if (holder == null || topInventory != holder.getInventory()) {
            return;
        }
        debug(player, holder.coordinates(), clickDetails(event));
        if (event.isShiftClick()) {
            event.setCancelled(true);
            debug(player, holder.coordinates(), "click rejected: reason=shift_transfer rawSlot=" + event.getRawSlot());
            return;
        }
        int topSize = topInventory.getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < topSize && !ingredientSlotSet(topInventory).contains(rawSlot)) {
            event.setCancelled(true);
            debug(player, holder.coordinates(), "click rejected: reason=non_ingredient_slot rawSlot=" + rawSlot);
            return;
        }
        if (rawSlot >= 0 && rawSlot < topSize && rejectsRecipeInput(holder.coordinates(), event.getCursor(), player)) {
            event.setCancelled(true);
            debug(player, holder.coordinates(), "click rejected: reason=recipe_or_fluid_validation rawSlot=" + rawSlot
                    + " item=" + describe(event.getCursor()));
            sendInputRejected(player);
            return;
        }
        debug(player, holder.coordinates(), "click allowed: rawSlot=" + rawSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        JuicerGuiHolder holder = openSessions.get(player.getUniqueId());
        if (holder == null || topInventory != holder.getInventory()) {
            return;
        }
        debug(player, holder.coordinates(), "drag evaluated: type=" + event.getType() + " rawSlots=" + event.getRawSlots()
                + " oldCursor=" + describe(event.getOldCursor()));
        int topSize = topInventory.getSize();
        Set<Integer> ingredientSlots = ingredientSlotSet(topInventory);
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize && !ingredientSlots.contains(rawSlot)) {
                event.setCancelled(true);
                debug(player, holder.coordinates(), "drag rejected: reason=non_ingredient_slot rawSlot=" + rawSlot);
                return;
            }
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize
                    && rejectsRecipeInput(holder.coordinates(), event.getNewItems().get(rawSlot), player)) {
                ItemStack newItem = event.getNewItems().get(rawSlot);
                event.setCancelled(true);
                debug(player, holder.coordinates(), "drag rejected: reason=recipe_or_fluid_validation rawSlot=" + rawSlot
                        + " item=" + describe(newItem));
                sendInputRejected(player);
                return;
            }
        }
        debug(player, holder.coordinates(), "drag allowed: rawSlots=" + event.getRawSlots());
    }

    List<Integer> ingredientSlots(Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        int size = inventory.getSize();
        List<Integer> slots = new ArrayList<>();
        for (Integer slot : settingsService.juicerIngredientSlots()) {
            if (slot != null && slot >= 0 && slot < size) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    Set<Integer> ingredientSlotSet(Inventory inventory) {
        return Set.copyOf(ingredientSlots(inventory));
    }

    String identifySource(ItemStack itemStack) {
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    private boolean rejectsRecipeInput(StationCoordinates coordinates, ItemStack itemStack, Player player) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        String source = identifySource(itemStack);
        RecipeDocument recipe = Texts.isBlank(source) ? null : recipeService.findJuicerRecipe(source, player);
        if (settingsService.onlyRecipeItems(StationType.JUICER) && recipe == null) {
            return true;
        }
        if (recipe == null || !recipeService.juicerHasFluidMode(recipe) || coordinates == null || runtimeService == null) {
            return false;
        }
        JuicerState state = runtimeService.loadStateOrEmpty(coordinates);
        return state.hasFluid() && !state.fluidId().equalsIgnoreCase(recipeService.juicerFluidId(recipe));
    }

    private void sendInputRejected(Player player) {
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "general.input_rejected", Map.of());
    }

    private void debug(Player player, StationCoordinates coordinates, String message) {
        GuiDebugSupport.log(plugin, player, "cooking station=" + StationType.JUICER
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
