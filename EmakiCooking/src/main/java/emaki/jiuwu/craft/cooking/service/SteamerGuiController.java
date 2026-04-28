package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
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
    private final SteamerStateCodec codec;
    private final Map<UUID, SteamerGuiHolder> openSessions = new ConcurrentHashMap<>();

    private SteamerRuntimeService runtimeService;

    SteamerGuiController(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            ItemSourceService itemSourceService,
            SteamerStateCodec codec) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.itemSourceService = itemSourceService;
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
            return false;
        }
        SteamerGuiHolder existingHolder = findOpenSession(coordinates);
        if (existingHolder != null && !player.getUniqueId().equals(existingHolder.viewerId())) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.in_use", Map.of());
            return true;
        }
        SteamerGuiHolder holder = new SteamerGuiHolder(player.getUniqueId(), coordinates);
        Inventory inventory = createInventory(holder);
        if (inventory == null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.open_failed", Map.of());
            return false;
        }
        holder.attach(inventory);
        loadInventory(coordinates, inventory);
        openSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.opened", Map.of());
        return true;
    }

    Inventory createInventory(SteamerGuiHolder holder) {
        String title = MiniMessages.legacy(MiniMessages.parse(settingsService.steamerInventoryTitle()));
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
        for (SteamerGuiHolder holder : List.copyOf(openSessions.values())) {
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
                    InventoryItemUtil.giveOrDrop(player, itemStack);
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
        if (!(event.getInventory().getHolder() instanceof SteamerGuiHolder holder)) {
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
        SteamerState state = runtimeService.saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        if (runtimeService.tickProcessor().shouldRemainActive(state, System.currentTimeMillis())) {
            runtimeService.activeStations().add(holder.coordinates());
            runtimeService.ensureTicker();
        } else if (state.isCompletelyEmpty()) {
            runtimeService.activeStations().remove(holder.coordinates());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SteamerGuiHolder)) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getInventory().getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < topSize && !ingredientSlotSet(event.getInventory()).contains(rawSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SteamerGuiHolder)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        Set<Integer> ingredientSlots = ingredientSlotSet(event.getInventory());
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize && !ingredientSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
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
}
