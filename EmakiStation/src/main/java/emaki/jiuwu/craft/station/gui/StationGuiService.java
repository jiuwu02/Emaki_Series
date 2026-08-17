package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import emaki.jiuwu.craft.corelib.unlock.UnlockService;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.config.GuiSettings;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.dismantle.DismantleGuiInteractionController;
import emaki.jiuwu.craft.station.dismantle.DismantleGuiRenderer;
import emaki.jiuwu.craft.station.dismantle.DismantleRecipeDefinition;
import emaki.jiuwu.craft.station.dismantle.DismantleService;
import emaki.jiuwu.craft.station.dismantle.DismantleStationDefinition;
import emaki.jiuwu.craft.station.dismantle.DismantleStationRegistry;
import emaki.jiuwu.craft.station.dismantle.DismantleViewState;
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
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.material.OutputDelivery;

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
    private final BiFunction<Player, String, String> placeholders;
    private final StationCatalogRenderer catalogRenderer;
    private final StationPreviewRenderer previewRenderer;
    private final StationQueueRenderer queueRenderer;
    private final DismantleGuiRenderer dismantleRenderer;
    private final DismantleGuiInteractionController dismantleController;
    private final DismantleService dismantleService;
    private final Supplier<DismantleStationRegistry> dismantleRegistrySupplier;
    private final ItemSourceService itemSourceService;
    private final Map<UUID, StationViewState> states = new ConcurrentHashMap<>();
    private final Map<UUID, DismantleViewState> dismantleStates = new ConcurrentHashMap<>();

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
            BiFunction<Player, String, String> placeholders,
            ConfiguredGuiSupport guiSupport,
            DismantleService dismantleService,
            Supplier<DismantleStationRegistry> dismantleRegistrySupplier) {
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
        this.itemSourceService = itemSourceService;
        this.placeholders = placeholders == null ? (player, text) -> text : placeholders;
        this.catalogRenderer = new StationCatalogRenderer(itemSourceService,
                () -> guiService.configuredItemService(), guiSupport);
        this.previewRenderer = new StationPreviewRenderer(itemSourceService,
                () -> guiService.configuredItemService(), guiSupport);
        this.queueRenderer = new StationQueueRenderer(
                () -> guiService.configuredItemService(), guiSupport);
        this.dismantleService = dismantleService;
        this.dismantleRegistrySupplier = dismantleRegistrySupplier;
        this.dismantleRenderer = new DismantleGuiRenderer(itemSourceService,
                () -> guiService.configuredItemService(), guiSupport);
        this.dismantleController = new DismantleGuiInteractionController(dismantleService,
                new OutputDelivery(itemSourceService, storageChannel),
                itemSourceService);
    }

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

    private interface SlotRenderer {

        ItemStack render(GuiTemplate.ResolvedSlot slot);
    }

    public EmakiResult<Unit> openDismantle(Player player, String stationId) {
        if (player == null || stationId == null) {
            return EmakiResult.invalidInput("station.open_bad_request");
        }
        if (threadOwnership != null && !threadOwnership.isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        DismantleStationDefinition station = dismantleRegistrySupplier.get().find(stationId);
        if (station == null) {
            return EmakiResult.notFound("station.unknown_station");
        }

        DismantleRecipeDefinition recipe = null;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemSourceRef ref = itemSourceService.identifyItem(item);
            if (ref == null) {
                continue;
            }
            List<DismantleRecipeDefinition> matches = dismantleService.findMatching(ref, station.id());
            if (!matches.isEmpty()) {
                recipe = matches.getFirst();
                break;
            }
        }
        if (recipe == null) {
            return EmakiResult.notFound("station.dismantle_no_matching_item");
        }
        DismantleViewState state = new DismantleViewState(player, station);
        state.selectedRecipe(recipe);
        dismantleStates.put(player.getUniqueId(), state);
        return openDismantlePage(state);
    }

    private EmakiResult<Unit> openDismantlePage(DismantleViewState state) {
        GuiTemplate template = templateOf(state.station().layoutId());
        if (template == null) {
            return EmakiResult.notFound("station.missing_layout");
        }
        Player viewer = state.viewer();
        state.beginNavigation();
        GuiSession guiSession = guiService.open(new GuiOpenRequest(plugin,
                viewer,
                template,
                dismantleRenderer.titleReplacements(state),
                (ignored, slot) -> dismantleRenderer.render(state, slot),
                new DismantleSessionHandler(state)));
        if (guiSession == null) {
            state.consumeNavigation();
            dismantleStates.remove(viewer.getUniqueId());
            return EmakiResult.internalError("station.open_failed");
        }
        if (viewer.getOpenInventory().getTopInventory() != guiSession.getInventory()) {
            state.consumeNavigation();
            dismantleStates.remove(viewer.getUniqueId());
            viewer.closeInventory();
            return EmakiResult.internalError("station.open_mismatch");
        }
        state.attach(guiSession);
        return EmakiResult.ok();
    }

    public void close(UUID playerId) {
        states.remove(playerId);
        dismantleStates.remove(playerId);
    }

    public void closeAll() {
        states.clear();
        dismantleStates.clear();
    }

    public void refreshOpenSessions() {
        for (StationViewState state : states.values()) {
            redraw(state);
        }
    }

    public List<UUID> viewers() {
        return new ArrayList<>(states.keySet());
    }

    public StationViewState state(UUID playerId) {
        return playerId == null ? null : states.get(playerId);
    }

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

    private boolean unlocked(Player viewer, RecipeDefinition recipe) {
        if (!recipe.hasDisplayCondition()) {
            return true;
        }
        return ConditionEvaluator.evaluate(recipe.displayCondition(),
                text -> placeholders.apply(viewer, text),
                ConditionContext.of(viewer));
    }

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

    private UnlockService.Quote quoteOf(StationViewState state, int slots) {
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

    private void redrawDismantle(DismantleViewState state) {
        GuiSession guiSession = state.guiSession();
        if (guiSession == null) {
            return;
        }
        if (threadOwnership == null || threadOwnership.isEntityOwned(state.viewer())) {
            guiSession.refresh();
        }
    }

    private final class StationSessionHandler implements GuiSessionHandler {

        private final StationViewState state;

        private StationSessionHandler(StationViewState state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession guiSession,
                GuiClickContext click,
                GuiTemplate.ResolvedSlot slot) {

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
                case DISMANTLE -> {

                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession guiSession, GuiClickContext click) {

            if (click.isBlockedTransfer()) {
                click.setCancelled(true);
            }
        }

        @Override
        public void onDrag(GuiSession guiSession, GuiDragContext drag) {

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

                }
            }
        }

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

                }
            }
        }

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
                    == QueueEntryState.PENDING_CLAIM) {
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
                case DISMANTLE -> {

                }
            }
            redraw(state);
        }
    }

    private final class DismantleSessionHandler implements GuiSessionHandler {

        private final DismantleViewState state;

        private DismantleSessionHandler(DismantleViewState state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession guiSession,
                GuiClickContext click,
                GuiTemplate.ResolvedSlot slot) {
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
            dismantleController.onClick(state, type, click.isRightClick(), slot,
                    () -> redrawDismantle(state),
                    () -> openDismantle(state.viewer(), state.station().id()));
        }

        @Override
        public void onPlayerInventoryClick(GuiSession guiSession, GuiClickContext click) {
            if (click.isBlockedTransfer()) {
                click.setCancelled(true);
            }
        }

        @Override
        public void onDrag(GuiSession guiSession, GuiDragContext drag) {
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
            if (state.consumeNavigation()) {
                return;
            }
            dismantleStates.remove(state.viewer().getUniqueId(), state);
        }
    }
}
