package emaki.jiuwu.craft.dungeon.rewardbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.service.MessageService;
import org.bukkit.persistence.PersistentDataType;

public final class RewardBoxGui implements Listener {

    private static final int CONTENT_SLOTS = 45;
    private static final int NAV_ROW_START = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_TOGGLE = 47;
    private static final int SLOT_NEXT = 49;
    private static final int SLOT_INFO = 51;
    private static final int SLOT_CLOSE = 53;

    static final class BoxHolder implements InventoryHolder {

        private final UUID playerId;
        private final int page;
        private final int visibleCount;
        private Inventory inventory;

        BoxHolder(UUID playerId, int page, int visibleCount) {
            this.playerId = playerId;
            this.page = page;
            this.visibleCount = visibleCount;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RewardBoxService service;
    private final MessageService messages;

    public RewardBoxGui(RewardBoxService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    public boolean serviceEnabled() {
        return service.enabled();
    }

    public void open(Player player, int page) {
        RewardBoxStore.Box box = service.store().load(player.getUniqueId());
        int capacity = service.visibleCapacity(player);
        int visibleCount = Math.min(box.items().size(), capacity);
        int totalPages = Math.max(1, (visibleCount + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        int targetPage = Math.max(0, Math.min(page, totalPages - 1));
        Inventory inventory = render(player, box, targetPage, visibleCount, totalPages);
        player.openInventory(inventory);
    }

    private Inventory render(Player player, RewardBoxStore.Box box, int page,
            int visibleCount, int totalPages) {
        BoxHolder holder = new BoxHolder(player.getUniqueId(), page, visibleCount);
        String title = messages.message("rewardbox.gui.title", Map.of(
                "page", String.valueOf(page + 1),
                "total", String.valueOf(totalPages),
                "items", String.valueOf(visibleCount)
        ));
        Inventory inventory = Bukkit.createInventory(holder, 54, MiniMessages.parse(title));
        holder.inventory = inventory;
        int from = page * CONTENT_SLOTS;
        int to = Math.min(from + CONTENT_SLOTS, visibleCount);
        for (int index = from; index < to; index++) {
            inventory.setItem(index - from, box.items().get(index));
        }
        inventory.setItem(SLOT_PREV, navItem(Material.ARROW, "rewardbox.gui.prev"));
        boolean autoFlow = box.autoFlow();
        inventory.setItem(SLOT_TOGGLE, navItem(
                autoFlow ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                autoFlow ? "rewardbox.gui.toggle_on" : "rewardbox.gui.toggle_off"));
        inventory.setItem(SLOT_NEXT, navItem(Material.ARROW, "rewardbox.gui.next"));
        inventory.setItem(SLOT_INFO, navItem(Material.BOOK, "rewardbox.gui.info"));
        inventory.setItem(SLOT_CLOSE, navItem(Material.BARRIER, "rewardbox.gui.close"));
        for (int slot = NAV_ROW_START; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, navItem(Material.GRAY_STAINED_GLASS_PANE, "rewardbox.gui.filler"));
            }
        }
        return inventory;
    }

    private ItemStack navItem(Material material, String messageKey) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessages.parse(messages.message(messageKey, Map.of())));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("emakidungeon", "reward_nav"),
                PersistentDataType.STRING, "1");
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BoxHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < CONTENT_SLOTS) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        switch (slot) {
            case SLOT_PREV -> {
                if (holder.page > 0) {
                    saveContents(holder, event.getInventory());
                    open(player, holder.page - 1);
                }
            }
            case SLOT_NEXT -> {
                if ((holder.page + 1) * CONTENT_SLOTS < holder.visibleCount) {
                    saveContents(holder, event.getInventory());
                    open(player, holder.page + 1);
                }
            }
            case SLOT_TOGGLE -> {
                boolean target = !service.isAutoFlow(holder.playerId);
                service.setAutoFlow(holder.playerId, target);
                messages.send(player, target ? "rewardbox.gui.auto_enabled"
                        : "rewardbox.gui.auto_disabled", Map.of());
                event.getInventory().setItem(SLOT_TOGGLE, navItem(
                        target ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                        target ? "rewardbox.gui.toggle_on" : "rewardbox.gui.toggle_off"));
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BoxHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= CONTENT_SLOTS && rawSlot < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BoxHolder holder) {
            saveContents(holder, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Inventory inventory = event.getPlayer().getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof BoxHolder holder) {
            saveContents(holder, inventory);
        }
    }

    private void saveContents(BoxHolder holder, Inventory inventory) {
        int pageStart = holder.page * CONTENT_SLOTS;
        int contentShown = Math.min(CONTENT_SLOTS,
                Math.max(0, holder.visibleCount - pageStart));
        service.store().update(holder.playerId, box -> {
            List<ItemStack> merged = new ArrayList<>();
            if (pageStart > 0) {
                merged.addAll(box.items().subList(0, Math.min(pageStart, box.items().size())));
            }
            for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack != null && stack.getType() != Material.AIR) {
                    merged.add(stack.clone());
                }
            }
            int tailFrom = Math.min(box.items().size(), pageStart + contentShown);
            merged.addAll(box.items().subList(tailFrom, box.items().size()));
            box.items().clear();
            box.items().addAll(merged);
        });
    }
}
