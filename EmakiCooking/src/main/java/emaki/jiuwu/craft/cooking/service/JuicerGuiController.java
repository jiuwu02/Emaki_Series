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
            return false;
        }
        JuicerGuiHolder existingHolder = findOpenSession(coordinates);
        if (existingHolder != null && !player.getUniqueId().equals(existingHolder.viewerId())) {
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
        for (JuicerGuiHolder holder : List.copyOf(openSessions.values())) {
            if (!coordinates.equals(holder.coordinates())) {
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof JuicerGuiHolder holder)) {
            return;
        }
        openSessions.remove(holder.viewerId(), holder);
        if (holder.suppressSave() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        JuicerState state = runtimeService.saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        if (state.isCompletelyEmpty()) {
            runtimeService.removeState(holder.coordinates(), true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof JuicerGuiHolder holder)) {
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
        if (rawSlot >= 0 && rawSlot < topSize && rejectsRecipeInput(holder.coordinates(), event.getCursor(), player)) {
            event.setCancelled(true);
            sendInputRejected(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof JuicerGuiHolder holder)) {
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
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize && rejectsRecipeInput(holder.coordinates(), event.getNewItems().get(rawSlot), player)) {
                event.setCancelled(true);
                sendInputRejected(player);
                return;
            }
        }
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
}
