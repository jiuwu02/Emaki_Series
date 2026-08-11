package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class OvenRuntimeService implements Listener {

    private static final long DIRTY_FLUSH_INTERVAL_TICKS = 100L;

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final ItemSourceService itemSourceService;
    private final OvenStateCodec codec;
    private final OvenTickProcessor tickProcessor;
    private final OvenGuiController guiController;
    private final CookingTextDisplayService textDisplayService;
    private final ExecutionDispatcher executionDispatcher;
    private CookingCompletionCoordinator completionCoordinator;
    private final Map<StationCoordinates, OvenState> runtimeStates = new ConcurrentHashMap<>();
    private final Set<StationCoordinates> activeStations = ConcurrentHashMap.newKeySet();
    private final Set<StationCoordinates> dirtyStations = ConcurrentHashMap.newKeySet();
    private final Set<StationCoordinates> tickingStations = ConcurrentHashMap.newKeySet();
    private TaskHandle tickerTask;
    private TaskHandle flushTask;

    public OvenRuntimeService(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            CookingTextDisplayService textDisplayService,
            ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.recipeService = recipeService;
        this.itemSourceService = itemSourceService;
        this.textDisplayService = textDisplayService;
        this.executionDispatcher = executionDispatcher;
        this.codec = new OvenStateCodec();
        this.tickProcessor = new OvenTickProcessor(settingsService, recipeService, rewardService, itemSourceService, codec);
        this.guiController = new OvenGuiController(plugin, messageService, settingsService, itemSourceService, recipeService, codec);
        this.guiController.setRuntimeService(this);
    }

    public void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        this.completionCoordinator = completionCoordinator;
        tickProcessor.setCompletionCoordinator(completionCoordinator);
        if (completionCoordinator != null) {
            completionCoordinator.register(completionStateAccess());
        }
    }

    CookingStationStateAccess completionStateAccess() {
        return new CookingStationStateAccess() {
            @Override
            public StationType stationType() {
                return StationType.OVEN;
            }

            @Override
            public Map<String, Object> snapshot(StationCoordinates coordinates) {
                OvenState state = runtimeStates.get(coordinates);
                if (state == null) {
                    state = codec.readState(stateStore.load(coordinates));
                }
                return state == null || state.isCompletelyEmpty() ? null : codec.serializeState(coordinates, state);
            }

            @Override
            public CompletionStage<Void> replace(StationCoordinates coordinates, Map<String, Object> committedState) {
                OvenState state = codec.readState(new MapYamlSection(committedState));
                if (state == null || state.isCompletelyEmpty()) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid committed oven state"));
                }
                return stateStore.saveAsync(coordinates, committedState)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            runtimeStates.put(coordinates, state);
                            dirtyStations.remove(coordinates);
                            if (tickProcessor.shouldRemainActive(state, System.currentTimeMillis())) {
                                activeStations.add(coordinates);
                                ensureTicker();
                            } else {
                                activeStations.remove(coordinates);
                                if (activeStations.isEmpty()) {
                                    cancelTicker();
                                }
                            }
                            refreshText(coordinates, state);
                        }));
            }

            @Override
            public CompletionStage<Void> delete(StationCoordinates coordinates) {
                return stateStore.deleteAsync(coordinates)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            guiController.closeOpenInventories(coordinates, true);
                            removeState(coordinates, false);
                            activeStations.remove(coordinates);
                            if (activeStations.isEmpty()) {
                                cancelTicker();
                            }
                        }));
            }
        };
    }

    OvenTickProcessor tickProcessor() {
        return tickProcessor;
    }

    Set<StationCoordinates> activeStations() {
        return activeStations;
    }

    public void reload() {
        guiController.closeAllOpenInventories(false);
        flushDirtyStates();
        cancelFlushTask();
        cancelTicker();
        textDisplayService.removeStationType(StationType.OVEN);
        activeStations.clear();
        runtimeStates.clear();
        dirtyStations.clear();
        stateStore.forEachLoadedState(StationType.OVEN, this::restoreStoredState);
        ensureTicker();
    }

    public boolean restoreStoredState(StationCoordinates coordinates, YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        OvenState state = codec.readState(section);
        ItemSourceRef stationSource = stateStore.stationSource(section);
        Block block = coordinates.block();
        if (state == null) {
            guiController.closeOpenInventories(coordinates, true);
            removeState(coordinates, false);
            return false;
        }
        if (!blockMatcher.matches(block, StationType.OVEN, stationSource)) {
            guiController.closeOpenInventories(coordinates, true);
            removeState(coordinates, false);
            plugin.getLogger().warning("Station restore report: skipped_mismatch type=oven coordinate=" + coordinates.runtimeKey());
            return false;
        }
        cacheState(coordinates, state);
        refreshText(coordinates, state);
        if (tickProcessor.shouldRemainActive(state, System.currentTimeMillis())) {
            activeStations.add(coordinates);
        }
        ensureTicker();
        return true;
    }

    public void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        OvenState state = runtimeStates.get(coordinates);
        if (state != null && !state.isCompletelyEmpty()) {
            stateStore.save(coordinates, codec.serializeState(coordinates, state));
        }
        removeState(coordinates, false);
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    public void shutdown() {
        guiController.closeAllOpenInventories(false);
        cancelTicker();
        waitForInFlightTicks();
        flushDirtyStates();
        cancelFlushTask();
        textDisplayService.removeStationType(StationType.OVEN);
        activeStations.clear();
        runtimeStates.clear();
        dirtyStations.clear();
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand()) {
            return false;
        }
        if (!blockMatcher.matches(interaction, StationType.OVEN)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.OVEN, coordinates)) {
            interaction.cancel();
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        CookingSettingsService.OvenFuelRule fuelRule = matchFuelRule(hand);
        if (fuelRule != null && settingsService.matchesInteraction(StationType.OVEN, CookingSettingsService.INTERACTION_FUEL, interaction)) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.OVEN_FUEL)
                    && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return addFuel(player, coordinates, hand, fuelRule);
        }
        if ((hand == null || hand.getType().isAir())
                && settingsService.matchesInteraction(StationType.OVEN, CookingSettingsService.INTERACTION_INSPECT, interaction)) {
            interaction.cancel();
            return showInfo(player, coordinates);
        }
        if (!settingsService.matchesInteraction(StationType.OVEN, CookingSettingsService.INTERACTION_OPEN, interaction)) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.OVEN_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        interaction.cancel();
        return guiController.openGui(player, coordinates);
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(context, StationType.OVEN)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, context.stationSource());
        OvenGuiHolder openHolder = guiController.findOpenSession(coordinates);
        OvenState state = openHolder == null
                ? loadStateOrEmpty(coordinates)
                : guiController.snapshotInventoryState(
                        coordinates,
                        openHolder.getInventory(),
                        openHolder.viewerId(),
                        Bukkit.getPlayer(openHolder.viewerId()) == null ? "" : Bukkit.getPlayer(openHolder.viewerId()).getName()
                );
        if (state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.OVEN, coordinates);
            return false;
        }
        guiController.closeOpenInventories(coordinates, true);
        tickProcessor.dropStoredItems(block, state);
        activeStations.remove(coordinates);
        removeState(coordinates, true);
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        guiController.onInventoryClose(event);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        guiController.onInventoryClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        guiController.onInventoryDrag(event);
    }

    private boolean addFuel(Player player,
            StationCoordinates coordinates,
            ItemStack hand,
            CookingSettingsService.OvenFuelRule rule) {
        if (player == null || coordinates == null || hand == null || rule == null) {
            return false;
        }
        OvenState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long currentBurning = state.burningUntilMs();
        long durationMs = Math.max(0L, rule.durationSeconds()) * 1000L;
        long newBurning = currentBurning > now ? currentBurning + durationMs : now + durationMs;
        state.setBurningUntilMs(newBurning);
        state.setHeat(state.heat() + Math.max(0, rule.heat()));
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        CookingRuntimeUtil.takeOneFromMainHand(player);
        activeStations.add(coordinates);
        ensureTicker();
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "oven.add_fuel", Map.of(
                "item", itemDisplayName(hand),
                "seconds", Math.max(0L, (newBurning - now) / 1000L),
                "heat", state.heat()
        ));
        plugin.effectService().playActions(StationType.OVEN, "fuel", player);
        return true;
    }

    private boolean showInfo(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            return false;
        }
        OvenState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long remainingBurnTime = state.burningUntilMs() > now ? (state.burningUntilMs() - now) / 1000L : 0L;
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "oven.info", Map.of(
                "heat", state.heat(),
                "burning_time", remainingBurnTime,
                "progress", calculateProgressStatus(state)
        ));
        return true;
    }

    private String calculateProgressStatus(OvenState state) {
        if (state == null || state.slotSources().isEmpty()) {
            return messageService.message("oven.progress_not_started");
        }
        int totalRequired = 0;
        int totalProgress = 0;
        int validIngredients = 0;
        boolean allCompleted = true;
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            RecipeDocument recipe = recipeService.findOvenRecipe(entry.getValue(), null);
            if (recipe == null) {
                continue;
            }
            int required = recipeService.ovenBakeTimeSeconds(recipe);
            int progress = Math.min(required, state.progressAt(entry.getKey()));
            validIngredients++;
            totalRequired += required;
            totalProgress += progress;
            if (progress < required) {
                allCompleted = false;
            }
        }
        if (validIngredients <= 0) {
            return messageService.message("oven.progress_not_started");
        }
        if (allCompleted) {
            return messageService.message("oven.progress_completed");
        }
        if (totalRequired <= 0) {
            return "0.00%";
        }
        return String.format(Locale.ROOT, "%.2f%%", (double) totalProgress * 100.0D / (double) totalRequired);
    }

    void ensureTicker() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        if (tickerTask != null && !tickerTask.isCancelled()) {
            return;
        }
        tickerTask = executionDispatcher.runGlobalTimer(plugin, this::tick, 20L, 20L);
    }

    private void ensureFlushTask() {
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
            return;
        }
        if (flushTask != null && !flushTask.isCancelled()) {
            return;
        }
        flushTask = executionDispatcher.runGlobalTimer(
                plugin,
                this::flushDirtyStates,
                DIRTY_FLUSH_INTERVAL_TICKS,
                DIRTY_FLUSH_INTERVAL_TICKS
        );
    }

    private void flushDirtyStates() {
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
            return;
        }
        for (StationCoordinates coordinates : List.copyOf(dirtyStations)) {
            if (coordinates == null) {
                continue;
            }
            OvenState state = runtimeStates.get(coordinates);
            if (state == null || state.isCompletelyEmpty()) {
                dirtyStations.remove(coordinates);
                continue;
            }
            stateStore.saveAsync(coordinates, codec.serializeState(coordinates, state))
                    .thenAccept(saved -> {
                        if (Boolean.TRUE.equals(saved)) {
                            dirtyStations.remove(coordinates);
                        }
                        if (dirtyStations.isEmpty()) {
                            cancelFlushTask();
                        }
                    });
        }
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
        }
    }

    private void cancelFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void waitForInFlightTicks() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!tickingStations.isEmpty() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void tick() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        long now = System.currentTimeMillis();
        for (StationCoordinates coordinates : List.copyOf(activeStations)) {
            if (coordinates == null || !tickingStations.add(coordinates)) {
                continue;
            }
            Location location = coordinates.location(0.5D, 0.5D, 0.5D);
            if (location == null || location.getWorld() == null) {
                tickingStations.remove(coordinates);
                activeStations.remove(coordinates);
                continue;
            }
            TaskHandle handle = executionDispatcher.runAtLocation(plugin, location, () -> {
                try {
                    processStation(coordinates, now);
                } finally {
                    tickingStations.remove(coordinates);
                }
            });
            if (handle == null) {
                tickingStations.remove(coordinates);
            }
        }
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    private void processStation(StationCoordinates coordinates, long now) {
        Block block = coordinates == null ? null : coordinates.block();
        OvenState state = loadStateOrEmpty(coordinates);
        ItemSourceRef stationSource = stateStore.rememberedStationSource(coordinates);
        if (block == null || !blockMatcher.matches(block, StationType.OVEN, stationSource)) {
            guiController.closeOpenInventories(coordinates, true);
            removeState(coordinates, true);
            activeStations.remove(coordinates);
            return;
        }
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.OVEN, coordinates)) {
            return;
        }
        boolean changed = tickProcessor.processStation(coordinates, state, block, now);
        if (changed) {
            saveState(coordinates, state);
        }
        if (tickProcessor.shouldRemainActive(state, now)) {
            activeStations.add(coordinates);
            refreshText(coordinates, state);
        } else {
            activeStations.remove(coordinates);
            if (state.isCompletelyEmpty()) {
                removeState(coordinates, true);
            } else {
                refreshText(coordinates, state);
            }
        }
    }

    OvenState saveInventory(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        if (coordinates == null || inventory == null) {
            return new OvenState();
        }
        OvenState updated = guiController.snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
        saveState(coordinates, updated);
        return updated;
    }

    void saveState(StationCoordinates coordinates, OvenState state) {
        if (coordinates == null || state == null) {
            return;
        }
        if (state.isCompletelyEmpty()) {
            removeState(coordinates, true);
            return;
        }
        runtimeStates.put(coordinates, state);
        dirtyStations.add(coordinates);
        ensureFlushTask();
        refreshText(coordinates, state);
    }

    OvenState loadStateOrEmpty(StationCoordinates coordinates) {
        if (coordinates == null) {
            return new OvenState();
        }
        OvenState cached = runtimeStates.get(coordinates);
        if (cached != null) {
            return cached;
        }
        OvenState loaded = codec.readState(stateStore.load(coordinates));
        OvenState existing = runtimeStates.putIfAbsent(coordinates, loaded);
        return existing == null ? loaded : existing;
    }




    Optional<StationCoordinates> viewingStation(UUID viewerId) {
        return Optional.ofNullable(guiController.viewingCoordinates(viewerId));
    }




    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        OvenState state = loadStateOrEmpty(coordinates);
        if (state == null || state.isCompletelyEmpty()) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        Block heatBlock = block == null ? null : block.getRelative(BlockFace.DOWN);
        long now = System.currentTimeMillis();
        boolean burning = state.burningUntilMs() > now;
        long remaining = burning ? (state.burningUntilMs() - now) / 1000L : 0L;

        int totalRequired = 0;
        int totalProgress = 0;
        boolean allCompleted = !state.slotSources().isEmpty();
        String firstSource = "";
        RecipeDocument firstRecipe = null;
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            if (Texts.isBlank(firstSource)) {
                firstSource = entry.getValue();
            }
            RecipeDocument recipe = recipeService.findOvenRecipe(entry.getValue(), null);
            if (recipe == null) {
                allCompleted = false;
                continue;
            }
            if (firstRecipe == null) {
                firstRecipe = recipe;
            }
            int required = recipeService.ovenBakeTimeSeconds(recipe);
            int progress = Math.min(required, state.progressAt(entry.getKey()));
            totalRequired += required;
            totalProgress += progress;
            if (progress < required) {
                allCompleted = false;
            }
        }
        double percent = totalRequired > 0 ? Math.min(100.0D, (double) totalProgress * 100.0D / (double) totalRequired) : 0.0D;
        return Optional.of(new StationSnapshot(
                StationType.OVEN,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(plugin, block),
                CookingRuntimeUtil.resolveBlockId(plugin, heatBlock),
                burning,
                remaining,
                state.heat(),
                0,
                0,
                MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(firstSource).orElse("")),
                Texts.toStringSafe(firstSource),
                firstSource.isBlank() ? 0 : 1,
                state.slotSources().size(),
                firstRecipe == null ? "" : firstRecipe.id(),
                firstRecipe == null ? "" : firstRecipe.displayName(),
                totalProgress,
                totalRequired,
                percent,
                allCompleted && totalRequired > 0,
                "",
                0,
                state.playerName() == null ? "" : state.playerName()
        ));
    }

    private void cacheState(StationCoordinates coordinates, OvenState state) {
        if (coordinates == null || state == null) {
            return;
        }
        runtimeStates.put(coordinates, state);
        dirtyStations.remove(coordinates);
    }

    private void removeState(StationCoordinates coordinates, boolean deleteFile) {
        if (coordinates == null) {
            return;
        }
        runtimeStates.remove(coordinates);
        dirtyStations.remove(coordinates);
        textDisplayService.removeStation(StationType.OVEN, coordinates);
        if (deleteFile) {
            stateStore.deleteAsync(coordinates);
        }
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
        }
    }

    private void refreshText(StationCoordinates coordinates, OvenState state) {
        if (!settingsService.textDisplayEnabled(StationType.OVEN) || coordinates == null
                || state == null || state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.OVEN, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.OVEN, coordinates);
            return;
        }
        long now = System.currentTimeMillis();
        long remainingBurn = state.burningUntilMs() > now ? (state.burningUntilMs() - now) / 1000L : 0L;
        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.oven.title"));
        appendLine(builder, messageService.message("text_display.oven.heat", Map.of("heat", state.heat())));
        appendLine(builder, messageService.message("text_display.oven.burning", Map.of("burning_time", remainingBurn)));
        if (state.hasSlots()) {
            appendLine(builder, messageService.message("text_display.oven.progress", Map.of("progress", calculateProgressStatus(state))));
        } else {
            appendLine(builder, messageService.message("text_display.oven.idle"));
        }
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.OVEN,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.OVEN)
        ));
    }

    private void appendLine(StringBuilder builder, String line) {
        if (Texts.isBlank(line)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private CookingSettingsService.OvenFuelRule matchFuelRule(ItemStack itemStack) {
        ItemSourceRef identified = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        if (identified == null) {
            return null;
        }
        for (CookingSettingsService.OvenFuelRule rule : settingsService.ovenFuels()) {
            if (rule != null && ItemSourceUtil.matches(rule.source(), identified)) {
                return rule;
            }
        }
        return null;
    }

    private String itemDisplayName(ItemStack itemStack) {
        String displayName = EmakiCoreLibApi.itemDisplayName(itemStack).orElse("");
        return Texts.isBlank(displayName)
                ? (itemStack == null || itemStack.getType() == null ? "" : itemStack.getType().name())
                : displayName;
    }
}
