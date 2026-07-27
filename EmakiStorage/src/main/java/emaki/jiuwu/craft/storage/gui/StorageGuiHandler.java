package emaki.jiuwu.craft.storage.gui;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.api.model.StorageResult;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.service.StorageCapacityService;
import emaki.jiuwu.craft.storage.service.StorageTransactionService;

/**
 * Click dispatch for the warehouse window.
 *
 * <p>Only {@link GuiClickContext} is consulted; {@code InventoryClickEvent} is never touched. Under
 * CoreLib's packet GUI backend no Bukkit event exists at all, so any direct dependency would fail
 * silently the moment an admin switches backends.
 *
 * <p>Deposits arrive through two paths that share one transaction implementation: clicking a display
 * slot with a loaded cursor, and the fixed {@code deposit_slot}. The only difference is the entry
 * check — the display path is refused while a search filter is active because paging no longer maps
 * to real slot numbers, whereas the deposit port keeps working.
 */
public final class StorageGuiHandler implements GuiSessionHandler {

    /** Callbacks the handler needs from the plugin without depending on the main class. */
    public interface Callbacks {

        /** Persists the storage after the window closes. */
        void onWindowClosed(Player viewer, PlayerStorage storage);

        /** Starts the chat prompt for a custom withdrawal amount. */
        void promptWithdrawAmount(Player viewer, GuiSession session, StorageKey key);

        /** Starts the chat prompt for a search term. */
        void promptSearch(Player viewer, GuiSession session);

        /** Opens the purchase flow. */
        void openUnlock(Player viewer, GuiSession session, PlayerStorage storage);

        /** Cycles or reverses the sort mode. */
        void cycleSort(Player viewer, GuiSession session, PlayerStorage storage, boolean reverse);

        /** Clears an active search filter. */
        void clearSearch(Player viewer, GuiSession session);
    }

    private final Plugin plugin;
    private final PlayerStorage storage;
    private final StorageGuiService guiService;
    private final StorageTransactionService transactionService;
    private final StorageCapacityService capacityService;
    private final MessageService messageService;
    private final Callbacks callbacks;

    private volatile AppConfig config;

    public StorageGuiHandler(Plugin plugin,
            PlayerStorage storage,
            StorageGuiService guiService,
            StorageTransactionService transactionService,
            StorageCapacityService capacityService,
            MessageService messageService,
            Callbacks callbacks,
            AppConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.guiService = guiService;
        this.transactionService = transactionService;
        this.capacityService = capacityService;
        this.messageService = messageService;
        this.callbacks = callbacks;
        this.config = config;
    }

    public Plugin plugin() {
        return plugin;
    }

    public PlayerStorage storage() {
        return storage;
    }

    public void reconfigure(AppConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        click.setCancelled(true);
        if (slot == null || slot.definition() == null || slot.definition().type() == null) {
            return;
        }
        Player viewer = session.viewer();
        if (viewer == null) {
            return;
        }
        if (rejectedClick(click.clickType())) {
            messageService.send(viewer, "gui.click.unsupported");
            return;
        }
        switch (slot.definition().type()) {
            case StorageLayoutResolver.TYPE_STORAGE_SLOT -> handleStorageSlot(session, click, slot, viewer);
            case StorageLayoutResolver.TYPE_DEPOSIT_SLOT -> handleDepositPort(session, click, viewer);
            case StorageLayoutResolver.TYPE_DEPOSIT_ALL -> handleDepositAll(session, viewer);
            case StorageLayoutResolver.TYPE_PAGE_PREV -> handlePage(session, viewer, -1);
            case StorageLayoutResolver.TYPE_PAGE_NEXT -> handlePage(session, viewer, 1);
            case StorageLayoutResolver.TYPE_SORT -> callbacks.cycleSort(viewer, session, storage,
                    click.clickType().isShiftVariant());
            case StorageLayoutResolver.TYPE_SEARCH -> handleSearchButton(session, click, viewer);
            case StorageLayoutResolver.TYPE_UNLOCK -> callbacks.openUnlock(viewer, session, storage);
            default -> {
            }
        }
    }

    /**
     * {@return whether a click kind is refused outright}
     *
     * <p>Double-click aggregates across slots, number keys and offhand swap bypass cursor
     * semantics, and drop keys would discard the item — none of them can be given a safe meaning
     * here. Distinguishing them at all is what the expanded {@link GuiClickType} set is for.
     */
    private boolean rejectedClick(GuiClickType clickType) {
        return switch (clickType) {
            case DOUBLECLICK, NUMBER_KEY, SWAP_OFFHAND, DROP, CONTROL_DROP -> true;
            default -> false;
        };
    }

    private void handleStorageSlot(GuiSession session,
            GuiClickContext click,
            GuiTemplate.ResolvedSlot slot,
            Player viewer) {
        StorageGuiService.ViewState state = guiService.viewState(viewer.getUniqueId());
        ItemStack cursor = click.cursorItem();
        boolean cursorLoaded = cursor != null && !cursor.getType().isAir();

        if (cursorLoaded) {
            // With require_empty_cursor_for_withdraw enabled a loaded cursor makes a display-slot
            // click a no-op instead of a deposit, which is the whole point of the option: it closes
            // the "meant to withdraw but had a leftover cursor" mis-click window.
            if (config.gui().requireEmptyCursorForWithdraw()) {
                messageService.send(viewer, "gui.deposit.cursor_guard");
                return;
            }
            if (state.searching()) {
                // Paging follows the filtered result set, so a display slot no longer identifies a
                // real slot. The deposit port stays available and is advertised in the item lore.
                messageService.send(viewer, "gui.deposit.search_blocked");
                return;
            }
            depositFromCursor(session, click, viewer, click.clickType() == GuiClickType.RIGHTCLICK ? 1 : -1);
            return;
        }
        withdrawFromSlot(session, click, slot, viewer, state);
    }

    private void withdrawFromSlot(GuiSession session,
            GuiClickContext click,
            GuiTemplate.ResolvedSlot slot,
            Player viewer,
            StorageGuiService.ViewState state) {
        int page = StorageGuiService.currentPage(session);
        int index = page * guiService.slotsPerPage() + slot.slotIndex();
        List<StorageKey> visible = state.visible();
        if (index < 0 || index >= visible.size()) {
            return;
        }
        StorageKey key = visible.get(index);
        StorageEntry entry = storage.entry(key);
        if (entry == null || entry.empty()) {
            return;
        }
        if (click.clickType() == GuiClickType.MIDDLECLICK) {
            if (config.behavior().withdrawPromptEnabled()) {
                callbacks.promptWithdrawAmount(viewer, session, key);
            }
            return;
        }
        long amount = withdrawAmountFor(click.clickType());
        if (amount <= 0L) {
            return;
        }
        StorageResult result = transactionService.withdraw(storage, viewer, key, amount,
                StorageOperationSource.GUI);
        reportWithdraw(viewer, result);
        guiService.refreshView(viewer, storage, state);
        clampAndRefresh(session, viewer, state);
    }

    private long withdrawAmountFor(GuiClickType clickType) {
        AppConfig.WithdrawAmounts amounts = config.behavior().withdrawAmounts();
        return switch (clickType) {
            case LEFTCLICK -> amounts.left();
            case RIGHTCLICK -> amounts.right();
            case SHIFT_LEFTCLICK -> amounts.shiftLeft();
            case SHIFT_RIGHTCLICK -> amounts.shiftRight();
            default -> 0L;
        };
    }

    private void handleDepositPort(GuiSession session, GuiClickContext click, Player viewer) {
        ItemStack cursor = click.cursorItem();
        if (cursor == null || cursor.getType().isAir()) {
            messageService.send(viewer, "gui.deposit.port_empty");
            return;
        }
        depositFromCursor(session, click, viewer, -1);
    }

    /**
     * Shared cursor deposit for both paths.
     *
     * @param amount how many units to take, or {@code -1} for the whole cursor
     */
    private void depositFromCursor(GuiSession session, GuiClickContext click, Player viewer, int amount) {
        ItemStack cursor = click.cursorItem();
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        StorageCapacity capacity = capacityService.capacityOf(storage, viewer, guiService.slotsPerPage());
        int requested = amount <= 0 ? cursor.getAmount() : amount;
        StorageTransactionService.CursorDepositResult result = transactionService.depositCursor(
                storage, viewer, capacity, cursor, requested, StorageOperationSource.GUI);
        if (result.result().applied()) {
            click.setCursor(result.remainingCursor());
        }
        reportDeposit(viewer, result.result());
        StorageGuiService.ViewState state = guiService.viewState(viewer.getUniqueId());
        guiService.refreshView(viewer, storage, state);
        clampAndRefresh(session, viewer, state);
    }

    private void handleDepositAll(GuiSession session, Player viewer) {
        StorageCapacity capacity = capacityService.capacityOf(storage, viewer, guiService.slotsPerPage());
        long stored = transactionService.depositAll(storage, viewer, capacity, StorageOperationSource.GUI);
        if (stored > 0L) {
            sendFeedback(viewer, "gui.deposit.bulk_success", Map.of("amount", stored));
        } else {
            messageService.send(viewer, "gui.deposit.bulk_empty");
        }
        StorageGuiService.ViewState state = guiService.viewState(viewer.getUniqueId());
        guiService.refreshView(viewer, storage, state);
        clampAndRefresh(session, viewer, state);
    }

    private void handlePage(GuiSession session, Player viewer, int delta) {
        StorageGuiService.ViewState state = guiService.viewState(viewer.getUniqueId());
        int page = StorageGuiService.currentPage(session);
        int target = page + delta;
        if (target < 0) {
            return;
        }
        int perPage = guiService.slotsPerPage();
        int visibleCount = state.visible().size();
        int reachable = Math.max(1, (int) Math.ceil((double) visibleCount / perPage));
        StorageCapacity capacity = capacityService.capacityOf(storage, viewer, perPage);
        int allowed = Math.min(reachable, Math.max(1, capacity.totalPages()));
        if (target > allowed - 1) {
            // A page with no entry at all is not reachable; page 1 always is.
            return;
        }
        guiService.applyPage(session, target);
    }

    private void handleSearchButton(GuiSession session, GuiClickContext click, Player viewer) {
        if (!config.search().enabled()) {
            messageService.send(viewer, "gui.search.disabled");
            return;
        }
        StorageGuiService.ViewState state = guiService.viewState(viewer.getUniqueId());
        if (state.searching() && click.clickType().isRightVariant()) {
            callbacks.clearSearch(viewer, session);
            return;
        }
        callbacks.promptSearch(viewer, session);
    }

    /**
     * Redirects a shift-click on a player inventory item into the deposit port logic.
     */
    @Override
    public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
        if (!click.isMoveToOtherInventory()) {
            if (click.isBlockedTransfer()) {
                click.setCancelled(true);
            }
            return;
        }
        click.setCancelled(true);
        Player viewer = session.viewer();
        if (viewer == null) {
            return;
        }
        ItemStack clicked = click.currentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        int slot = viewer.getInventory().first(clicked);
        if (slot < 0) {
            return;
        }
        StorageCapacity capacity = capacityService.capacityOf(storage, viewer, guiService.slotsPerPage());
        StorageResult result = transactionService.depositFromInventory(storage, viewer, capacity, slot,
                StorageOperationSource.GUI);
        reportDeposit(viewer, result);
        StorageGuiService.ViewState state = guiService.viewState(viewer.getUniqueId());
        guiService.refreshView(viewer, storage, state);
        clampAndRefresh(session, viewer, state);
    }

    @Override
    public void onClose(GuiSession session, GuiCloseContext close) {
        Player viewer = session.viewer();
        if (viewer != null) {
            guiService.releaseViewState(viewer.getUniqueId());
            callbacks.onWindowClosed(viewer, storage);
        }
    }

    /**
     * Clamps the page after the entry count changed, then repaints.
     *
     * <p>Withdrawing the last entry on page 3 makes that page unreachable, so the view falls back
     * to the last reachable page instead of showing an empty window.
     */
    private void clampAndRefresh(GuiSession session, Player viewer, StorageGuiService.ViewState state) {
        int perPage = guiService.slotsPerPage();
        int visibleCount = state.visible().size();
        int reachable = Math.max(1, (int) Math.ceil((double) visibleCount / perPage));
        int page = StorageGuiService.currentPage(session);
        int clamped = Math.min(page, reachable - 1);
        if (clamped != page) {
            guiService.applyPage(session, Math.max(0, clamped));
            return;
        }
        guiService.refresh(session);
    }

    private void reportDeposit(Player viewer, StorageResult result) {
        switch (result.status()) {
            case SUCCESS -> sendFeedback(viewer, "gui.deposit.success",
                    Map.of("amount", result.appliedAmount()));
            case PARTIAL -> sendFeedback(viewer, "gui.deposit.partial",
                    Map.of("amount", result.appliedAmount(), "remaining", result.remainingAmount()));
            case CANCELLED -> messageService.send(viewer, "gui.deposit.cancelled");
            case FAILED -> messageService.send(viewer, depositFailureKey(result.reasonKey()));
            case UNAVAILABLE -> messageService.send(viewer, "general.storage_unavailable");
        }
    }

    private String depositFailureKey(String reasonKey) {
        if (reasonKey == null) {
            return "gui.deposit.failed";
        }
        return switch (reasonKey) {
            case "filtered" -> "gui.deposit.filtered";
            case "unique_rejected" -> "gui.deposit.unique_rejected";
            case "no_free_slot" -> "gui.deposit.no_free_slot";
            case "slot_full" -> "gui.deposit.slot_full";
            case "inventory_conflict" -> "gui.deposit.inventory_conflict";
            default -> "gui.deposit.failed";
        };
    }

    private void reportWithdraw(Player viewer, StorageResult result) {
        switch (result.status()) {
            case SUCCESS -> {
            }
            case PARTIAL -> messageService.send(viewer, "gui.withdraw.partial",
                    Map.of("amount", result.appliedAmount(), "remaining", result.remainingAmount()));
            case CANCELLED -> messageService.send(viewer, "gui.withdraw.cancelled");
            case FAILED -> messageService.send(viewer, "gui.withdraw.failed");
            case UNAVAILABLE -> messageService.send(viewer, "general.storage_unavailable");
        }
    }

    /**
     * Sends deposit feedback through the configured channel.
     *
     * <p>Feedback is not optional decoration: under the mixed deposit strategy an item lands where
     * the entry order puts it, which may be a different page, so without feedback the player would
     * believe the item vanished.
     */
    private void sendFeedback(Player viewer, String key, Map<String, Object> replacements) {
        AppConfig.DepositFeedback feedback = config.gui().depositFeedback();
        if (feedback == AppConfig.DepositFeedback.NONE) {
            return;
        }
        String text = messageService.message(key, replacements);
        if (feedback == AppConfig.DepositFeedback.ACTIONBAR) {
            viewer.sendActionBar(messageService.render(text));
            return;
        }
        messageService.sendRaw(viewer, text);
    }
}
