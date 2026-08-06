package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.config.GuiSettings;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.material.MergedMaterialChannel;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.CraftQueue;
import emaki.jiuwu.craft.station.queue.PlayerQueues;
import emaki.jiuwu.craft.station.queue.QueueCapacity;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.QueueUnlockService;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.queue.StationQueueUnlockService;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * Opens the three station pages and routes their clicks.
 *
 * <h2>Why view state is not on the GUI session</h2>
 * {@code GuiService.open()} closes the viewer's current window first, which fires the previous handler's
 * {@code onClose}. Every page change therefore destroys a session. Page numbers live in {@link StationViewState},
 * kept in a map this service owns, so returning from the preview lands on the catalog page the viewer left.
 *
 * <h2>Why every player-side click is rejected explicitly</h2>
 * CoreLib cancels clicks in the upper inventory automatically, but not clicks in the player's own inventory, and
 * its blocked-transfer test does not cover number-key or offhand swaps. Since no slot in any of the three pages
 * accepts an item, every handler cancels unconditionally and only then decides what the click meant.
 *
 * <p>Nothing here takes custody of a player's items. There are no input slots to hand back on close, which is
 * what removes the whole class of "rendered display stack escapes into an inventory" bugs.
 */
public final class StationGuiService {

    private final Plugin plugin;
    private final GuiService guiService;
    private final ThreadOwnership threadOwnership;
    private final Supplier<GuiTemplateLoader> layoutLoader;
    private final Supplier<StationRegistry> registrySupplier;
    private final Supplier<GuiSettings> guiSettings;
    private final MergedMaterialChannel materialChannel;
    private final StorageChannel storageChannel;
    private final QueueService queueService;
    private final QueueUnlockService unlockService;
    private final StationQueueUnlockService purchaseService;
    private final StationCraftService craftService;
    private final EconomyManager economyManager;
    private final java.util.function.BiFunction<Player, String, String> placeholders;
    private final StationCatalogRenderer catalogRenderer;
    private final StationPreviewRenderer previewRenderer;
    private final StationQueueRenderer queueRenderer;
    private final Map<UUID, StationViewState> states = new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param plugin              the owning plugin
     * @param guiService          CoreLib's GUI service
     * @param threadOwnership     CoreLib's thread-ownership probe
     * @param layoutLoader        supplies the current layout loader
     * @param registrySupplier    supplies the current resolved registry
     * @param guiSettings         supplies the current GUI timing settings
     * @param itemSourceService   CoreLib's item-source service
     * @param materialChannel     the merged material channel
     * @param storageChannel      the warehouse channel, consulted for availability only
     * @param queueService        the queue cache
     * @param unlockService       the purchased-slot cache
     * @param purchaseService     the queue purchase service
     * @param craftService        the submission orchestrator
     * @param economyManager      CoreLib's economy manager, read for balance display
     * @param placeholders        resolves placeholders for one player, used by display conditions
     * @param guiSupport          reads each layout's virtual items and texts
     */
    public StationGuiService(Plugin plugin,
            GuiService guiService,
            ThreadOwnership threadOwnership,
            Supplier<GuiTemplateLoader> layoutLoader,
            Supplier<StationRegistry> registrySupplier,
            Supplier<GuiSettings> guiSettings,
            ItemSourceService itemSourceService,
            MergedMaterialChannel materialChannel,
            StorageChannel storageChannel,
            QueueService queueService,
            QueueUnlockService unlockService,
            StationQueueUnlockService purchaseService,
            StationCraftService craftService,
            EconomyManager economyManager,
            java.util.function.BiFunction<Player, String, String> placeholders,
            ConfiguredGuiSupport guiSupport) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.threadOwnership = threadOwnership;
        this.layoutLoader = layoutLoader;
        this.registrySupplier = registrySupplier;
        this.guiSettings = guiSettings;
        this.materialChannel = materialChannel;
        this.storageChannel = storageChannel;
        this.queueService = queueService;
        this.unlockService = unlockService;
        this.purchaseService = purchaseService;
        this.craftService = craftService;
        this.economyManager = economyManager;
        this.placeholders = placeholders == null ? (player, text) -> text : placeholders;
        this.catalogRenderer = new StationCatalogRenderer(itemSourceService,
                () -> guiService.configuredItemService(), guiSupport);
        this.previewRenderer = new StationPreviewRenderer(itemSourceService,
                () -> guiService.configuredItemService(), guiSupport);
        this.queueRenderer = new StationQueueRenderer(
                () -> guiService.configuredItemService(), guiSupport);
    }

    /**
     * Opens a station's catalog page.
     *
     * <p><strong>Thread:</strong> the viewer's owner thread. Returns {@code WRONG_THREAD} elsewhere rather than
     * scheduling a deferred open, so a caller never believes a window is already up when it is not.
     *
     * @param player    the viewer
     * @param stationId the station to open
     * @return success when the window opened, otherwise the reason it did not
     */
    public EmakiResult<Unit> open(Player player, String stationId) {
        if (player == null || stationId == null) {
            return EmakiResult.invalidInput("station.open_bad_request");
        }
        if (threadOwnership != null && !threadOwnership.isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        StationDefinition station = registrySupplier.get().station(stationId);
        if (station == null) {
            return EmakiResult.notFound("station.unknown_station");
        }
        if (queueService.cached(player.getUniqueId()) == null) {
            return EmakiResult.rejected("station.queue_not_loaded");
        }
        StationViewState state = new StationViewState(player, station);
        states.put(player.getUniqueId(), state);
        return openCatalog(state);
    }

    /**
     * Opens or reopens the catalog page for an existing state.
     *
     * @param state the viewer's page state
     * @return success when the window opened
     */
    private EmakiResult<Unit> openCatalog(StationViewState state) {
        GuiTemplate template = templateOf(state.station().layoutId());
        if (template == null) {
            return EmakiResult.notFound("station.missing_layout");
        }
        List<StationCatalogEntry> entries = catalogEntries(state);
        int pageSize = Math.max(1, GuiPagination.pageSize(template, StationSlotType.RECIPE_LIST));
        state.catalogPage(state.catalogPage(), GuiPagination.totalPages(entries.size(), pageSize));
        state.page(StationViewState.Page.CATALOG);
        return openPage(state, template,
                catalogRenderer.titleReplacements(state, entries),
                slot -> catalogRenderer.render(state, entries, slot));
    }

    /**
     * Opens the material preview for the state's selected recipe.
     *
     * @param state the viewer's page state
     * @return success when the window opened
     */
    private EmakiResult<Unit> openPreview(StationViewState state) {
        if (state.selectedRecipe() == null) {
            return EmakiResult.rejected("station.no_recipe_selected");
        }
        GuiTemplate template = templateOf(state.station().previewLayoutId());
        if (template == null) {
            return EmakiResult.notFound("station.missing_layout");
        }
        int pageSize = Math.max(1, GuiPagination.pageSize(template, StationSlotType.MATERIAL_LIST));
        state.materialPage(state.materialPage(),
                GuiPagination.totalPages(state.selectedRecipe().requirements().size(), pageSize));
        state.page(StationViewState.Page.PREVIEW);
        EmakiResult<Unit> opened = openPage(state, template,
                previewRenderer.titleReplacements(state),
                slot -> previewRenderer.render(state, maxBatchOf(state), balanceOf(state), slot));
        if (opened.isSuccess()) {
            refreshAvailability(state, true);
        }
        return opened;
    }

    /**
     * Opens the craft-queue page.
     *
     * @param state the viewer's page state
     * @return success when the window opened
     */
    private EmakiResult<Unit> openQueue(StationViewState state) {
        GuiTemplate template = templateOf(state.station().queueLayoutId());
        if (template == null) {
            return EmakiResult.notFound("station.missing_layout");
        }
        CraftQueue queue = queueOf(state);
        int pageSize = Math.max(1, GuiPagination.pageSize(template, StationSlotType.QUEUE_VIEW));
        int entryCount = queue == null ? 0 : queue.entries().size();
        state.queuePage(state.queuePage(), GuiPagination.totalPages(entryCount, pageSize));
        state.page(StationViewState.Page.QUEUE);
        return openPage(state, template,
                queueRenderer.titleReplacements(state, queue, capacityOf(state)),
                slot -> queueRenderer.render(state, queueOf(state), capacityOf(state),
                        purchasedOf(state), quoteOf(state, 1), slot));
    }

    /**
     * Shared open path for all three pages.
     *
     * <p>{@link StationViewState#beginNavigation()} is set before the open so the outgoing page's
     * {@code onClose} recognises this as a page change and leaves the view state alone.
     *
     * @param state    the viewer's page state
     * @param template the layout to open
     * @param title    the title substitutions
     * @param renderer the per-slot renderer
     * @return success when the window opened
     */
    private EmakiResult<Unit> openPage(StationViewState state,
            GuiTemplate template,
            Map<String, Object> title,
            SlotRenderer renderer) {
        Player viewer = state.viewer();
        state.beginNavigation();
        GuiSession guiSession = guiService.open(new GuiOpenRequest(plugin,
                viewer,
                template,
                title,
                (ignored, slot) -> renderer.render(slot),
                new StationSessionHandler(state)));
        if (guiSession == null) {
            state.consumeNavigation();
            states.remove(viewer.getUniqueId());
            return EmakiResult.internalError("station.open_failed");
        }
        if (viewer.getOpenInventory().getTopInventory() != guiSession.getInventory()) {
            state.consumeNavigation();
            states.remove(viewer.getUniqueId());
            viewer.closeInventory();
            return EmakiResult.internalError("station.open_mismatch");
        }
        state.attach(guiSession);
        return EmakiResult.ok();
    }

    /** Renders one resolved slot for whichever page is open. */
    private interface SlotRenderer {

        /**
         * Renders a slot.
         *
         * @param slot the resolved slot
         * @return the stack to place, or {@code null} for the layout's own definition
         */
        org.bukkit.inventory.ItemStack render(GuiTemplate.ResolvedSlot slot);
    }

    /**
     * Discards a viewer's page state.
     *
     * <p>No items are returned because none were ever held.
     *
     * @param playerId the viewer
     */
    public void close(UUID playerId) {
        states.remove(playerId);
    }

    /** Discards every viewer's page state. Used by the disable path. */
    public void closeAll() {
        states.clear();
    }

    /** Redraws every open window, which is how the ticker advances visible progress. */
    public void refreshOpenSessions() {
        for (StationViewState state : states.values()) {
            redraw(state);
        }
    }

    /** {@return every viewer with an open station window} */
    public List<UUID> viewers() {
        return new ArrayList<>(states.keySet());
    }

    /**
     * Returns a viewer's page state.
     *
     * @param playerId the viewer
     * @return the state, or {@code null} when they have no station window open
     */
    public StationViewState state(UUID playerId) {
        return playerId == null ? null : states.get(playerId);
    }

    /**
     * Builds the catalog rows visible to one viewer.
     *
     * <p>Three filters apply, in order of severity:
     *
     * <ol>
     *   <li>{@code visible: false} removes a recipe outright — it is not a catalog row at all;</li>
     *   <li>a failed permission check removes it too, matching the published meaning of {@code permission}
     *       as "can see and use";</li>
     *   <li>a failed {@code display_condition} keeps the row but marks it locked, so a player can see that
     *       progression continues.</li>
     * </ol>
     *
     * @param state the viewer's page state
     * @return the visible rows, in registry order
     */
    private List<StationCatalogEntry> catalogEntries(StationViewState state) {
        List<StationCatalogEntry> entries = new ArrayList<>();
        Player viewer = state.viewer();
        for (RecipeDefinition recipe : registrySupplier.get().recipesOf(state.station().id())) {
            if (!recipe.visible()) {
                continue;
            }
            if (recipe.hasPermission() && !viewer.hasPermission(recipe.permission())) {
                continue;
            }
            entries.add(new StationCatalogEntry(recipe, unlocked(viewer, recipe)));
        }
        return entries;
    }

    /**
     * Evaluates a recipe's display condition.
     *
     * @param viewer the viewer
     * @param recipe the recipe
     * @return whether the row should be shown as unlocked
     */
    private boolean unlocked(Player viewer, RecipeDefinition recipe) {
        if (!recipe.hasDisplayCondition()) {
            return true;
        }
        return ConditionEvaluator.evaluate(recipe.displayCondition(),
                text -> placeholders.apply(viewer, text),
                ConditionContext.of(viewer));
    }

    /**
     * Re-reads the viewer's combined material availability and redraws when it lands.
     *
     * <p>Throttled by {@code gui.refresh_interval} unless forced, because a preview redraw happens on every
     * ticker pass and a warehouse round trip per pass is not affordable.
     *
     * @param state the viewer's page state
     * @param force whether to bypass the staleness check
     */
    private void refreshAvailability(StationViewState state, boolean force) {
        RecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long maxAge = refreshIntervalMs();
        if (!force && !state.availabilityStale(now, maxAge)) {
            return;
        }
        state.availability(state.availability(), now);
        materialChannel.snapshotAsync(state.viewer(), state.station(), recipe)
                .thenAccept(availability -> {
                    state.availability(availability, System.currentTimeMillis());
                    updateBlockReason(state);
                    redraw(state);
                });
    }

    /**
     * Recomputes why the selected recipe cannot be submitted right now.
     *
     * @param state the viewer's page state
     */
    private void updateBlockReason(StationViewState state) {
        RecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null) {
            state.blockReason("no_recipe");
            return;
        }
        EmakiResult<Unit> gate = craftService.validate(state.viewer(), registrySupplier.get(),
                state.station(), recipe, state.batch());
        if (gate.isFailure()) {
            state.blockReason(gate.reasonKey());
            return;
        }
        if (materialChannel.plan(recipe, state.batch(), state.availability()) == null) {
            state.blockReason("insufficient_materials");
            return;
        }
        long charge = recipe.cost().totalFor(state.batch());
        if (charge > 0L && balanceOf(state) < (double) charge) {
            state.blockReason("insufficient_currency");
            return;
        }
        state.blockReason("");
    }

    private long maxBatchOf(StationViewState state) {
        RecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null) {
            return 0L;
        }
        long byMaterials = materialChannel.maxBatch(recipe, state.availability());
        if (!recipe.cost().charges()) {
            return byMaterials;
        }
        long byBalance = (long) (balanceOf(state) / (double) recipe.cost().amount());
        return Math.min(byMaterials, Math.max(0L, byBalance));
    }

    private double balanceOf(StationViewState state) {
        RecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null || !recipe.cost().charges() || economyManager == null) {
            return 0.0D;
        }
        return economyManager.getBalance(state.viewer(), recipe.cost().providerId(), "");
    }

    private CraftQueue queueOf(StationViewState state) {
        PlayerQueues queues = queueService.cached(state.viewer().getUniqueId());
        return queues == null ? null : queues.existingQueue(state.station().id());
    }

    private int purchasedOf(StationViewState state) {
        return unlockService == null
                ? 0
                : unlockService.purchased(state.viewer().getUniqueId(), state.station().id());
    }

    private int capacityOf(StationViewState state) {
        return QueueCapacity.effectiveLength(state.viewer(), state.station(), purchasedOf(state));
    }

    private StationQueueUnlockService.Quote quoteOf(StationViewState state, int slots) {
        if (purchaseService == null || unlockService == null) {
            return null;
        }
        return purchaseService.quote(state.viewer(), state.station(),
                unlockService.cached(state.viewer().getUniqueId()), slots);
    }

    private GuiTemplate templateOf(String layoutId) {
        GuiTemplateLoader loader = layoutLoader.get();
        return loader == null ? null : loader.get(layoutId);
    }

    private long refreshIntervalMs() {
        GuiSettings settings = guiSettings == null ? null : guiSettings.get();
        long ticks = settings == null ? 20L : settings.refreshTicks();
        return Math.max(50L, ticks * 50L);
    }

    private long clickThrottleMs() {
        GuiSettings settings = guiSettings == null ? null : guiSettings.get();
        return settings == null ? 0L : settings.clickThrottleMs();
    }

    private void redraw(StationViewState state) {
        GuiSession guiSession = state.guiSession();
        if (guiSession == null) {
            return;
        }
        if (threadOwnership == null || threadOwnership.isEntityOwned(state.viewer())) {
            guiSession.refresh();
        }
    }

    /**
     * Handles every interaction for one open page.
     *
     * <p>One instance per opened page, closing over the view state so no lookup is needed.
     */
    private final class StationSessionHandler implements GuiSessionHandler {

        private final StationViewState state;

        private StationSessionHandler(StationViewState state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession guiSession,
                GuiClickContext click,
                GuiTemplate.ResolvedSlot slot) {
            // No slot on any page accepts an item, so cancelling first is unconditional and the rest of this
            // method only has to decide what the click meant.
            click.setCancelled(true);
            if (slot == null || slot.definition() == null || state.processing()) {
                return;
            }
            if (click.isUnsupportedKeyboardClick()
                    || click.clickType() == GuiClickType.NUMBER_KEY
                    || click.clickType() == GuiClickType.SWAP_OFFHAND) {
                return;
            }
            if (!state.acceptClick(System.currentTimeMillis(), clickThrottleMs())) {
                return;
            }
            String type = StationSlotType.normalize(slot.definition().type());
            if (type.isEmpty()) {
                type = StationSlotType.normalize(slot.definition().key());
            }
            switch (state.page()) {
                case CATALOG -> onCatalogClick(type, click, slot);
                case PREVIEW -> onPreviewClick(type, click);
                case QUEUE -> onQueueClick(type, click, slot);
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession guiSession, GuiClickContext click) {
            // Shift-click, collect-to-cursor, and double-click can pull items into the upper inventory without
            // targeting a slot. Nothing up there is a container, so every such transfer is refused.
            if (click.isBlockedTransfer()) {
                click.setCancelled(true);
            }
        }

        @Override
        public void onDrag(GuiSession guiSession, GuiDragContext drag) {
            // A drag can span several slots at once, including rendered ones. Rather than reconciling which
            // parts were legal, refuse drags that touch the window at all.
            int topSize = guiSession.template().slotCount();
            for (Integer rawSlot : drag.rawSlots()) {
                if (rawSlot != null && rawSlot < topSize) {
                    drag.setCursor(drag.oldCursor());
                    return;
                }
            }
        }

        @Override
        public void onClose(GuiSession guiSession, GuiCloseContext close) {
            // A page change arrives as the same event as a real close. The flag set before opening the sibling
            // page distinguishes them; without it, every navigation would discard the viewer's page numbers.
            if (state.consumeNavigation()) {
                return;
            }
            states.remove(state.viewer().getUniqueId(), state);
        }

        private void onCatalogClick(String type, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            switch (type) {
                case StationSlotType.RECIPE_LIST -> onRecipeClick(click, slot);
                case StationSlotType.BATCH_MULTIPLIER -> {
                    cycleBatch(click.isRightClick());
                    redraw(state);
                }
                case StationSlotType.OUTPUT_TOGGLE -> {
                    cycleOutput();
                    redraw(state);
                }
                case StationSlotType.PREV_PAGE -> movePage(-1);
                case StationSlotType.NEXT_PAGE -> movePage(1);
                case StationSlotType.QUEUE_OPEN -> openQueue(state);
                case StationSlotType.CLOSE -> state.viewer().closeInventory();
                default -> {
                    // Decorative or unknown slot; the click is already cancelled.
                }
            }
        }

        /**
         * Handles a click on a catalog row.
         *
         * <p>Left click submits at the current batch; right click opens the material preview. A locked row does
         * neither.
         *
         * @param click the click
         * @param slot  the clicked slot
         */
        private void onRecipeClick(GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            List<StationCatalogEntry> entries = catalogEntries(state);
            int pageSize = Math.max(1, slot.definition().slots().size());
            int offset = state.catalogPage() * pageSize + slot.slotIndex();
            if (offset < 0 || offset >= entries.size()) {
                return;
            }
            StationCatalogEntry entry = entries.get(offset);
            if (!entry.unlocked()) {
                return;
            }
            if (click.isRightClick()) {
                state.selectedRecipe(entry.recipe());
                openPreview(state);
                return;
            }
            submit(entry.recipe());
        }

        private void onPreviewClick(String type, GuiClickContext click) {
            switch (type) {
                case StationSlotType.CONFIRM -> submit(state.selectedRecipe());
                case StationSlotType.BACK -> openCatalog(state);
                case StationSlotType.BATCH_MULTIPLIER -> {
                    cycleBatch(click.isRightClick());
                    updateBlockReason(state);
                    redraw(state);
                }
                case StationSlotType.MAX_CRAFTABLE -> {
                    state.batch(Math.max(1L, maxBatchOf(state)));
                    updateBlockReason(state);
                    redraw(state);
                }
                case StationSlotType.OUTPUT_TOGGLE -> {
                    cycleOutput();
                    redraw(state);
                }
                case StationSlotType.PREV_PAGE -> movePage(-1);
                case StationSlotType.NEXT_PAGE -> movePage(1);
                case StationSlotType.CLOSE -> state.viewer().closeInventory();
                default -> {
                    // Decorative or unknown slot; the click is already cancelled.
                }
            }
        }

        private void onQueueClick(String type, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            switch (type) {
                case StationSlotType.QUEUE_VIEW -> onQueueEntryClick(slot);
                case StationSlotType.CLAIM_ALL -> claimAll();
                case StationSlotType.QUEUE_PURCHASE -> purchase(click.isShiftClick());
                case StationSlotType.BACK -> openCatalog(state);
                case StationSlotType.PREV_PAGE -> movePage(-1);
                case StationSlotType.NEXT_PAGE -> movePage(1);
                case StationSlotType.CLOSE -> state.viewer().closeInventory();
                default -> {
                    // Decorative or unknown slot; the click is already cancelled.
                }
            }
        }

        /**
         * Cancels or claims one queue entry.
         *
         * <p>A {@code PENDING_CLAIM} entry has already finished, so clicking it hands the outputs over rather
         * than attempting a cancellation the craft service would refuse.
         *
         * @param slot the clicked slot
         */
        private void onQueueEntryClick(GuiTemplate.ResolvedSlot slot) {
            CraftQueue queue = queueOf(state);
            if (queue == null) {
                return;
            }
            int pageSize = Math.max(1, slot.definition().slots().size());
            int index = state.queuePage() * pageSize + slot.slotIndex();
            if (index < 0 || index >= queue.entries().size()) {
                return;
            }
            if (queue.entries().get(index).state()
                    == emaki.jiuwu.craft.station.api.model.QueueEntryState.PENDING_CLAIM) {
                claimAll();
                return;
            }
            state.processing(true);
            craftService.cancelAsync(state.viewer(), state.station(), index)
                    .whenComplete((result, error) -> {
                        state.processing(false);
                        redraw(state);
                    });
        }

        private void claimAll() {
            state.processing(true);
            craftService.claimAsync(state.viewer()).whenComplete((result, error) -> {
                state.processing(false);
                redraw(state);
            });
        }

        /**
         * Buys queue slots.
         *
         * <p>A plain click buys one; a shift click buys the largest configured batch that still fits under the
         * station ceiling.
         *
         * @param batchPurchase whether this is a shift click
         */
        private void purchase(boolean batchPurchase) {
            if (purchaseService == null || unlockService == null) {
                return;
            }
            int slots = 1;
            if (batchPurchase) {
                List<Integer> options = purchaseService.batchOptions();
                slots = options.isEmpty() ? 1 : options.getLast();
            }
            state.processing(true);
            unlockService.purchaseAsync(state.viewer(), state.station(), slots)
                    .whenComplete((result, error) -> {
                        state.processing(false);
                        redraw(state);
                    });
        }

        /**
         * Submits one craft.
         *
         * <p>The catalog has no availability snapshot, so a submission from there is validated entirely inside
         * the craft service and any refusal is reported through the usual result path. The preview page does have
         * a snapshot, which is why it can show a block reason before the click.
         *
         * @param recipe the recipe to submit
         */
        private void submit(RecipeDefinition recipe) {
            if (recipe == null) {
                return;
            }
            state.processing(true);
            craftService.submitAsync(state.viewer().getUniqueId(), state.station().id(),
                            recipe.id(), state.batch())
                    .whenComplete((result, error) -> {
                        state.processing(false);
                        if (state.page() == StationViewState.Page.PREVIEW) {
                            refreshAvailability(state, true);
                        } else {
                            redraw(state);
                        }
                    });
        }

        /**
         * Cycles the batch multiplier through fixed steps.
         *
         * <p>The catalog has no availability snapshot, so there is no "max" step to cycle into; the preview page
         * offers that through its own {@code max_craftable} slot instead.
         *
         * @param backwards whether to step backwards
         */
        private void cycleBatch(boolean backwards) {
            long[] steps = {1L, 10L, 64L, 1_000L};
            long current = state.batch();
            int position = 0;
            for (int index = 0; index < steps.length; index++) {
                if (steps[index] == current) {
                    position = index;
                    break;
                }
            }
            int next = backwards ? position - 1 : position + 1;
            if (next < 0) {
                next = steps.length - 1;
            }
            if (next >= steps.length) {
                next = 0;
            }
            state.batch(steps[next]);
        }

        private void cycleOutput() {
            if (!state.station().playerSwitchable()) {
                return;
            }
            OutputRouting[] options = OutputRouting.values();
            int position = 0;
            for (int index = 0; index < options.length; index++) {
                if (options[index] == state.outputRouting()) {
                    position = index;
                    break;
                }
            }
            state.outputRouting(options[(position + 1) % options.length]);
        }

        /**
         * Moves whichever list the open page paginates.
         *
         * @param delta the page delta
         */
        private void movePage(int delta) {
            GuiSession guiSession = state.guiSession();
            if (guiSession == null) {
                return;
            }
            GuiTemplate template = guiSession.template();
            switch (state.page()) {
                case CATALOG -> {
                    int pageSize = Math.max(1,
                            GuiPagination.pageSize(template, StationSlotType.RECIPE_LIST));
                    state.catalogPage(state.catalogPage() + delta,
                            GuiPagination.totalPages(catalogEntries(state).size(), pageSize));
                }
                case PREVIEW -> {
                    RecipeDefinition recipe = state.selectedRecipe();
                    if (recipe == null) {
                        return;
                    }
                    int pageSize = Math.max(1,
                            GuiPagination.pageSize(template, StationSlotType.MATERIAL_LIST));
                    state.materialPage(state.materialPage() + delta,
                            GuiPagination.totalPages(recipe.requirements().size(), pageSize));
                }
                case QUEUE -> {
                    CraftQueue queue = queueOf(state);
                    int pageSize = Math.max(1,
                            GuiPagination.pageSize(template, StationSlotType.QUEUE_VIEW));
                    state.queuePage(state.queuePage() + delta, GuiPagination.totalPages(
                            queue == null ? 0 : queue.entries().size(), pageSize));
                }
            }
            redraw(state);
        }
    }
}
