package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.material.BackpackChannel;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.CraftQueue;
import emaki.jiuwu.craft.station.queue.PlayerQueues;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.station.recipe.RecipeMatcher;

/**
 * Opens station windows and routes their clicks.
 *
 * <h2>Why every player-side click is rejected explicitly</h2>
 * CoreLib's GUI service cancels clicks in the upper inventory automatically, but not clicks in the player's
 * own inventory, and its blocked-transfer test does not cover number-key or offhand swaps. Left alone, those
 * paths can move an item into a slot the renderer will overwrite on the next redraw, which duplicates it.
 * Every handler below therefore cancels first and only then decides what the click meant.
 *
 * <p>Input slots hold the player's property. They are returned on close, on disconnect, and on disable. They
 * are not persisted, so a hard crash loses them; the layout text says the input area is not storage.
 */
public final class StationGuiService implements StationMaterialView {

    private final Plugin plugin;
    private final GuiService guiService;
    private final ThreadOwnership threadOwnership;
    private final Supplier<GuiTemplateLoader> layoutLoader;
    private final Supplier<StationRegistry> registrySupplier;
    private final ItemSourceService itemSourceService;
    private final BackpackChannel backpackChannel;
    private final StorageChannel storageChannel;
    private final QueueService queueService;
    private final StationCraftService craftService;
    private final StationGuiRenderer renderer;
    private final Map<UUID, StationGuiSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<ItemSourceRef, Long>> storageCounts = new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param plugin            the owning plugin
     * @param guiService        CoreLib's GUI service
     * @param threadOwnership   CoreLib's thread-ownership probe
     * @param layoutLoader      supplies the current layout loader
     * @param registrySupplier  supplies the current resolved registry
     * @param itemSourceService CoreLib's item-source service
     * @param backpackChannel   the inventory channel
     * @param storageChannel    the warehouse channel
     * @param queueService      the queue cache
     * @param craftService      the submission orchestrator
     * @param guiSupport        reads the layout's virtual items and texts
     */
    public StationGuiService(Plugin plugin,
            GuiService guiService,
            ThreadOwnership threadOwnership,
            Supplier<GuiTemplateLoader> layoutLoader,
            Supplier<StationRegistry> registrySupplier,
            ItemSourceService itemSourceService,
            BackpackChannel backpackChannel,
            StorageChannel storageChannel,
            QueueService queueService,
            StationCraftService craftService,
            ConfiguredGuiSupport guiSupport) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.threadOwnership = threadOwnership;
        this.layoutLoader = layoutLoader;
        this.registrySupplier = registrySupplier;
        this.itemSourceService = itemSourceService;
        this.backpackChannel = backpackChannel;
        this.storageChannel = storageChannel;
        this.queueService = queueService;
        this.craftService = craftService;
        this.renderer = new StationGuiRenderer(itemSourceService,
                () -> guiService.configuredItemService(), guiSupport, this);
    }

    /**
     * Opens a station window.
     *
     * <p><strong>Thread:</strong> the viewer's owner thread. Returns {@code WRONG_THREAD} elsewhere rather
     * than scheduling a deferred open, so a caller never believes a window is already up when it is not.
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
        GuiTemplateLoader loader = layoutLoader.get();
        GuiTemplate template = loader == null ? null : loader.get(station.layoutId());
        if (template == null) {
            return EmakiResult.notFound("station.missing_layout");
        }
        PlayerQueues queues = queueService.cached(player.getUniqueId());
        if (queues == null) {
            return EmakiResult.rejected("station.queue_not_loaded");
        }
        StationGuiSession session = new StationGuiSession(player, station, storageChannel.usable());
        GuiSession guiSession = guiService.open(new GuiOpenRequest(plugin,
                player,
                template,
                renderer.titleReplacements(session),
                (ignored, slot) -> renderer.render(session, slot),
                new StationSessionHandler(session)));
        if (guiSession == null) {
            return EmakiResult.internalError("station.open_failed");
        }
        if (player.getOpenInventory().getTopInventory() != guiSession.getInventory()) {
            player.closeInventory();
            return EmakiResult.internalError("station.open_mismatch");
        }
        session.attach(guiSession);
        sessions.put(player.getUniqueId(), session);
        refreshMatch(session);
        return EmakiResult.ok();
    }

    /**
     * Closes a viewer's window and returns their input items.
     *
     * @param playerId the viewer
     */
    public void close(UUID playerId) {
        StationGuiSession session = sessions.remove(playerId);
        if (session == null) {
            return;
        }
        returnInputs(session);
        storageCounts.remove(playerId);
    }

    /** Closes every window, returning input items. Used by the disable path. */
    public void closeAll() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            StationGuiSession session = sessions.remove(playerId);
            if (session != null) {
                returnInputs(session);
            }
        }
        storageCounts.clear();
    }

    /** Redraws every open window, which is how the ticker advances visible progress. */
    public void refreshOpenSessions() {
        for (StationGuiSession session : sessions.values()) {
            GuiSession guiSession = session.guiSession();
            if (guiSession != null) {
                guiSession.refresh();
            }
        }
    }

    /**
     * Returns a viewer's session.
     *
     * @param playerId the viewer
     * @return the session, or {@code null} when they have no window open
     */
    public StationGuiSession session(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    @Override
    public long ownedOf(StationGuiSession session, MaterialRequirement requirement) {
        Map<ItemSourceRef, Long> available = availableOf(session);
        long total = 0L;
        for (ItemSourceRef source : requirement.sources()) {
            total += available.getOrDefault(source, 0L);
        }
        return total;
    }

    @Override
    public Map<ItemSourceRef, Long> availableOf(StationGuiSession session) {
        if (session.channel() == MaterialChannel.STORAGE) {
            return storageCounts.getOrDefault(session.viewer().getUniqueId(), Map.of());
        }
        return backpackChannel.available(session.inputs());
    }

    @Override
    public CraftQueue queueOf(StationGuiSession session) {
        PlayerQueues queues = queueService.cached(session.viewer().getUniqueId());
        return queues == null ? null : queues.existingQueue(session.station().id());
    }

    @Override
    public boolean storageUsable() {
        return storageChannel.usable();
    }

    private void returnInputs(StationGuiSession session) {
        Player viewer = session.viewer();
        for (ItemStack stack : session.drainInputs()) {
            InventoryItemUtil.addOrDrop(viewer, stack);
        }
    }

    private void refreshMatch(StationGuiSession session) {
        if (session.channel() == MaterialChannel.STORAGE) {
            refreshStorageCounts(session);
            return;
        }
        RecipeMatcher matcher = new RecipeMatcher(registrySupplier.get());
        session.currentMatch(matcher.match(session.station().id(), availableOf(session)));
        updateBlockReason(session);
        redraw(session);
    }

    private void refreshStorageCounts(StationGuiSession session) {
        RecipeDefinition selected = session.selectedRecipe();
        if (selected == null) {
            List<RecipeDefinition> stationRecipes = registrySupplier.get()
                    .recipesOf(session.station().id());
            if (stationRecipes.isEmpty()) {
                session.currentMatch(emaki.jiuwu.craft.station.recipe.RecipeMatch.none());
                updateBlockReason(session);
                redraw(session);
                return;
            }
            selected = stationRecipes.getFirst();
            session.selectedRecipe(selected);
        }
        RecipeDefinition target = selected;
        storageChannel.countAsync(session.viewer().getUniqueId(), target).thenAccept(counts -> {
            storageCounts.put(session.viewer().getUniqueId(), counts);
            RecipeMatcher matcher = new RecipeMatcher(registrySupplier.get());
            long max = matcher.maxBatch(target, counts);
            session.currentMatch(new emaki.jiuwu.craft.station.recipe.RecipeMatch(
                    target, List.of(target), max));
            session.selectedRecipe(target);
            updateBlockReason(session);
            redraw(session);
        });
    }

    private void updateBlockReason(StationGuiSession session) {
        RecipeDefinition recipe = session.selectedRecipe();
        if (recipe == null) {
            session.blockReason("no_recipe");
            return;
        }
        EmakiResult<Unit> gate = craftService.validate(session.viewer(), registrySupplier.get(),
                session.station(), recipe, session.batch());
        if (gate.isFailure()) {
            session.blockReason(gate.reasonKey());
            return;
        }
        RecipeMatcher matcher = new RecipeMatcher(registrySupplier.get());
        if (!matcher.supports(recipe, availableOf(session), session.batch())) {
            session.blockReason("insufficient_materials");
            return;
        }
        session.blockReason("");
    }

    private void redraw(StationGuiSession session) {
        GuiSession guiSession = session.guiSession();
        if (guiSession == null) {
            return;
        }
        if (threadOwnership == null || threadOwnership.isEntityOwned(session.viewer())) {
            guiSession.refresh();
        }
    }

    /**
     * Handles every interaction for one open window.
     *
     * <p>One instance per session, so it closes over that session's state without any lookup.
     */
    private final class StationSessionHandler implements GuiSessionHandler {

        private final StationGuiSession session;

        private StationSessionHandler(StationGuiSession session) {
            this.session = session;
        }

        @Override
        public void onSlotClick(GuiSession guiSession,
                GuiClickContext click,
                GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                click.setCancelled(true);
                return;
            }
            if (session.processing()) {
                click.setCancelled(true);
                return;
            }
            String type = StationSlotType.normalize(slot.definition().type());
            if (type.isEmpty()) {
                type = StationSlotType.normalize(slot.definition().key());
            }
            // Keyboard-driven clicks bypass the normal cursor protocol; a number-key or offhand swap into a
            // rendered slot would be overwritten by the next redraw and effectively duplicated.
            if (click.isUnsupportedKeyboardClick()
                    || (!StationSlotType.INPUT.equals(type)
                            && (click.clickType() == emaki.jiuwu.craft.corelib.gui.GuiClickType.NUMBER_KEY
                                    || click.clickType()
                                            == emaki.jiuwu.craft.corelib.gui.GuiClickType.SWAP_OFFHAND))) {
                click.setCancelled(true);
                return;
            }
            switch (type) {
                case StationSlotType.INPUT -> handleInputClick(click, slot);
                case StationSlotType.CHANNEL_TOGGLE -> {
                    click.setCancelled(true);
                    toggleChannel();
                }
                case StationSlotType.BATCH_MULTIPLIER -> {
                    click.setCancelled(true);
                    cycleBatch(click.isShiftClick(), click.isRightClick());
                }
                case StationSlotType.MAX_CRAFTABLE -> {
                    click.setCancelled(true);
                    session.batch(Math.max(1L, session.currentMatch().maxBatch()));
                    updateBlockReason(session);
                    redraw(session);
                }
                case StationSlotType.OUTPUT_TOGGLE -> {
                    click.setCancelled(true);
                    cycleOutput();
                }
                case StationSlotType.RECIPE_PREVIEW -> {
                    click.setCancelled(true);
                    session.cycleAlternative();
                    updateBlockReason(session);
                    redraw(session);
                }
                case StationSlotType.PREV_PAGE -> {
                    click.setCancelled(true);
                    session.materialPage(session.materialPage() - 1);
                    redraw(session);
                }
                case StationSlotType.NEXT_PAGE -> {
                    click.setCancelled(true);
                    session.materialPage(session.materialPage() + 1);
                    redraw(session);
                }
                case StationSlotType.QUEUE_VIEW -> {
                    click.setCancelled(true);
                    handleQueueClick(slot);
                }
                case StationSlotType.CONFIRM -> {
                    click.setCancelled(true);
                    submit();
                }
                default -> click.setCancelled(true);
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession guiSession, GuiClickContext click) {
            // Shift-click, collect-to-cursor, and double-click can all pull items into the upper inventory
            // without ever targeting a specific slot. Refusing them keeps the input area under this handler's
            // sole control; players place materials by clicking the input slots directly.
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
            close(session.viewer().getUniqueId());
        }

        private void handleInputClick(GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            click.setCancelled(true);
            ItemStack cursor = click.cursorItem();
            ItemStack held = session.inputs().get(slot.inventorySlot());
            boolean cursorEmpty = cursor == null || cursor.getType().isAir();
            boolean heldEmpty = held == null || held.getType().isAir();
            if (cursorEmpty && heldEmpty) {
                return;
            }
            if (cursorEmpty) {
                session.inputs().remove(slot.inventorySlot());
                click.setCursor(held);
            } else if (heldEmpty) {
                session.inputs().put(slot.inventorySlot(), cursor.clone());
                click.setCursor(null);
            } else {
                session.inputs().put(slot.inventorySlot(), cursor.clone());
                click.setCursor(held);
            }
            refreshMatch(session);
        }

        private void handleQueueClick(GuiTemplate.ResolvedSlot slot) {
            CraftQueue queue = queueOf(session);
            if (queue == null) {
                return;
            }
            int index = slot.slotIndex();
            if (index < 0 || index >= queue.entries().size()) {
                return;
            }
            session.processing(true);
            craftService.cancelAsync(session.viewer(), session.station(), index)
                    .whenComplete((result, error) -> {
                        session.processing(false);
                        refreshMatch(session);
                    });
        }

        private void toggleChannel() {
            StationDefinition station = session.station();
            if (!station.backpackChannel() || !station.storageChannel()) {
                return;
            }
            if (session.channel() == MaterialChannel.BACKPACK) {
                if (!storageChannel.usable()) {
                    return;
                }
                session.channel(MaterialChannel.STORAGE);
            } else {
                session.channel(MaterialChannel.BACKPACK);
                session.selectedRecipe(null);
            }
            session.batch(1L);
            refreshMatch(session);
        }

        private void cycleBatch(boolean shift, boolean rightClick) {
            long max = Math.max(1L, session.currentMatch().maxBatch());
            if (shift) {
                session.batch(max);
                updateBlockReason(session);
                redraw(session);
                return;
            }
            long[] steps = {1L, 10L, 64L, 1_000L, max};
            long current = session.batch();
            int position = 0;
            for (int index = 0; index < steps.length; index++) {
                if (steps[index] == current) {
                    position = index;
                    break;
                }
            }
            int next = rightClick ? position - 1 : position + 1;
            if (next < 0) {
                next = steps.length - 1;
            }
            if (next >= steps.length) {
                next = 0;
            }
            session.batch(Math.max(1L, steps[next]));
            updateBlockReason(session);
            redraw(session);
        }

        private void cycleOutput() {
            if (!session.station().playerSwitchable()) {
                return;
            }
            OutputRouting[] options = OutputRouting.values();
            int position = 0;
            for (int index = 0; index < options.length; index++) {
                if (options[index] == session.outputRouting()) {
                    position = index;
                    break;
                }
            }
            session.outputRouting(options[(position + 1) % options.length]);
            redraw(session);
        }

        private void submit() {
            RecipeDefinition recipe = session.selectedRecipe();
            if (recipe == null || !session.blockReason().isEmpty()) {
                return;
            }
            session.processing(true);
            if (session.channel() == MaterialChannel.STORAGE) {
                craftService.submitFromStorageAsync(session.viewer().getUniqueId(),
                                session.station().id(), recipe.id(), session.batch())
                        .whenComplete((result, error) -> {
                            session.processing(false);
                            refreshMatch(session);
                        });
                return;
            }
            List<ConsumedMaterial> consumed =
                    backpackChannel.consume(session.inputs(), recipe, session.batch());
            if (consumed == null) {
                session.processing(false);
                session.blockReason("insufficient_materials");
                redraw(session);
                return;
            }
            if (!craftService.fireSubmitEvent(session.viewer(), session.station(), recipe,
                    session.batch(), MaterialChannel.BACKPACK)) {
                // The event was vetoed after consumption was planned, so hand the materials straight back
                // rather than keeping them.
                for (ConsumedMaterial material : consumed) {
                    backpackChannel.refund(session.viewer(), material.source(), material.amount());
                }
                session.processing(false);
                refreshMatch(session);
                return;
            }
            craftService.submitFromInputs(session.viewer(), session.station(), recipe,
                            session.batch(), consumed)
                    .whenComplete((result, error) -> {
                        session.processing(false);
                        refreshMatch(session);
                    });
        }
    }

    /**
     * Aggregates the identities currently in a session's input slots.
     *
     * @param session the station session
     * @return the counts per identity
     */
    public Map<ItemSourceRef, Long> inputCounts(StationGuiSession session) {
        Map<ItemSourceRef, Long> counts = new LinkedHashMap<>();
        if (itemSourceService == null) {
            return counts;
        }
        for (ItemStack stack : session.inputs().values()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ItemSourceRef ref = itemSourceService.identifyItem(stack);
            if (ref != null) {
                counts.merge(ref, (long) stack.getAmount(), Long::sum);
            }
        }
        return counts;
    }

    /** {@return every viewer with an open window} */
    public List<UUID> viewers() {
        return new ArrayList<>(sessions.keySet());
    }
}
