package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

abstract class TickingStationRuntimeService<S> {

    private static final long DIRTY_FLUSH_INTERVAL_TICKS = 100L;

    protected final EmakiCookingPlugin plugin;
    protected final CookingBlockMatcher blockMatcher;
    protected final StationStateStore stateStore;
    protected final CookingTextDisplayService textDisplayService;
    private final EmakiScheduling taskScheduler;
    private final Map<StationCoordinates, S> runtimeStates = new ConcurrentHashMap<>();
    private final Set<StationCoordinates> activeStations = ConcurrentHashMap.newKeySet();
    private final Set<StationCoordinates> dirtyStations = ConcurrentHashMap.newKeySet();
    private final Set<StationCoordinates> tickingStations = ConcurrentHashMap.newKeySet();
    private CookingCompletionCoordinator completionCoordinator;
    private TaskToken tickerTask;
    private TaskToken flushTask;

    protected TickingStationRuntimeService(EmakiCookingPlugin plugin,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingTextDisplayService textDisplayService,
            EmakiScheduling taskScheduler) {
        this.plugin = plugin;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.textDisplayService = textDisplayService;
        this.taskScheduler = taskScheduler;
    }

    protected abstract StationType stationType();

    protected abstract S createEmptyState();

    protected abstract boolean stateCompletelyEmpty(S state);

    protected abstract S readState(YamlSection section);

    protected abstract Map<String, Object> serializeState(StationCoordinates coordinates, S state);

    protected abstract boolean shouldRemainActive(S state, long now);

    protected abstract boolean processStationTick(StationCoordinates coordinates, S state, Block block, long now);

    protected abstract void bindCompletionCoordinator(CookingCompletionCoordinator completionCoordinator);

    protected abstract void refreshText(StationCoordinates coordinates, S state);

    protected abstract void closeOpenInventories(StationCoordinates coordinates, boolean suppressSave);

    protected abstract void closeAllOpenInventories(boolean suppressSave);

    protected abstract S snapshotInventoryState(StationCoordinates coordinates,
            Inventory inventory,
            UUID playerUuid,
            String playerName);

    protected abstract StationCoordinates viewingCoordinates(UUID viewerId);

    public void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        this.completionCoordinator = completionCoordinator;
        bindCompletionCoordinator(completionCoordinator);
        if (completionCoordinator != null) {
            completionCoordinator.register(completionStateAccess());
        }
    }

    protected final CookingCompletionCoordinator completionCoordinator() {
        return completionCoordinator;
    }

    CookingStationStateAccess completionStateAccess() {
        return new CookingStationStateAccess() {
            @Override
            public StationType stationType() {
                return TickingStationRuntimeService.this.stationType();
            }

            @Override
            public Map<String, Object> snapshot(StationCoordinates coordinates) {
                S state = runtimeStates.get(coordinates);
                if (state == null) {
                    state = readState(stateStore.load(coordinates));
                }
                return state == null || stateCompletelyEmpty(state) ? null : serializeState(coordinates, state);
            }

            @Override
            public CompletionStage<Void> replace(StationCoordinates coordinates, Map<String, Object> committedState) {
                S state = readState(new MapYamlSection(committedState));
                if (state == null || stateCompletelyEmpty(state)) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException(
                            "Invalid committed " + stationType().folderName() + " state"));
                }
                return stateStore.saveAsync(coordinates, committedState)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            runtimeStates.put(coordinates, state);
                            dirtyStations.remove(coordinates);
                            if (shouldRemainActive(state, System.currentTimeMillis())) {
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
                            closeOpenInventories(coordinates, true);
                            removeState(coordinates, false);
                            activeStations.remove(coordinates);
                            if (activeStations.isEmpty()) {
                                cancelTicker();
                            }
                        }));
            }
        };
    }

    Set<StationCoordinates> activeStations() {
        return activeStations;
    }

    public void reload() {
        closeAllOpenInventories(false);
        flushDirtyStates();
        cancelFlushTask();
        cancelTicker();
        textDisplayService.removeStationType(stationType());
        activeStations.clear();
        runtimeStates.clear();
        dirtyStations.clear();
        stateStore.forEachLoadedState(stationType(), this::restoreStoredState);
        ensureTicker();
    }

    public boolean restoreStoredState(StationCoordinates coordinates, YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        S state = readState(section);
        ItemSourceRef stationSource = stateStore.stationSource(section);
        Block block = coordinates.block();
        if (state == null) {
            closeOpenInventories(coordinates, true);
            removeState(coordinates, false);
            return false;
        }
        if (!blockMatcher.matches(block, stationType(), stationSource)) {
            closeOpenInventories(coordinates, true);
            removeState(coordinates, false);
            plugin.getLogger().warning("Station restore report: skipped_mismatch type="
                    + stationType().folderName() + " coordinate=" + coordinates.runtimeKey());
            return false;
        }
        cacheState(coordinates, state);
        refreshText(coordinates, state);
        if (shouldRemainActive(state, System.currentTimeMillis())) {
            activeStations.add(coordinates);
        }
        ensureTicker();
        return true;
    }

    public void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        S state = runtimeStates.get(coordinates);
        if (state != null && !stateCompletelyEmpty(state)) {
            stateStore.save(coordinates, serializeState(coordinates, state));
        }
        removeState(coordinates, false);
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    public void shutdown() {
        closeAllOpenInventories(false);
        cancelTicker();
        waitForInFlightTicks();
        flushDirtyStates();
        cancelFlushTask();
        textDisplayService.removeStationType(stationType());
        activeStations.clear();
        runtimeStates.clear();
        dirtyStations.clear();
    }

    void ensureTicker() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        if (tickerTask != null && !tickerTask.cancelled()) {
            return;
        }
        tickerTask = taskScheduler.runGlobalTimer(plugin, this::tick, 20L, 20L);
    }

    private void ensureFlushTask() {
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
            return;
        }
        if (flushTask != null && !flushTask.cancelled()) {
            return;
        }
        flushTask = taskScheduler.runGlobalTimer(
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
            S state = runtimeStates.get(coordinates);
            if (state == null || stateCompletelyEmpty(state)) {
                dirtyStations.remove(coordinates);
                continue;
            }
            stateStore.saveAsync(coordinates, serializeState(coordinates, state))
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
            TaskToken handle = taskScheduler.runAtLocation(plugin, location, () -> {
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
        S state = loadStateOrEmpty(coordinates);
        ItemSourceRef stationSource = stateStore.rememberedStationSource(coordinates);
        if (block == null || !blockMatcher.matches(block, stationType(), stationSource)) {
            closeOpenInventories(coordinates, true);
            removeState(coordinates, true);
            activeStations.remove(coordinates);
            return;
        }
        if (completionCoordinator != null && completionCoordinator.hasActive(stationType(), coordinates)) {
            return;
        }
        boolean changed = processStationTick(coordinates, state, block, now);
        if (changed) {
            saveState(coordinates, state);
        }
        if (shouldRemainActive(state, now)) {
            activeStations.add(coordinates);
            refreshText(coordinates, state);
        } else {
            activeStations.remove(coordinates);
            if (stateCompletelyEmpty(state)) {
                removeState(coordinates, true);
            } else {
                refreshText(coordinates, state);
            }
        }
    }

    S saveInventory(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        if (coordinates == null || inventory == null) {
            return createEmptyState();
        }
        S updated = snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
        saveState(coordinates, updated);
        return updated;
    }

    void saveState(StationCoordinates coordinates, S state) {
        if (coordinates == null || state == null) {
            return;
        }
        if (stateCompletelyEmpty(state)) {
            removeState(coordinates, true);
            return;
        }
        runtimeStates.put(coordinates, state);
        dirtyStations.add(coordinates);
        ensureFlushTask();
        refreshText(coordinates, state);
    }

    S loadStateOrEmpty(StationCoordinates coordinates) {
        if (coordinates == null) {
            return createEmptyState();
        }
        S cached = runtimeStates.get(coordinates);
        if (cached != null) {
            return cached;
        }
        S loaded = readState(stateStore.load(coordinates));
        S existing = runtimeStates.putIfAbsent(coordinates, loaded);
        return existing == null ? loaded : existing;
    }

    Optional<StationCoordinates> viewingStation(UUID viewerId) {
        return Optional.ofNullable(viewingCoordinates(viewerId));
    }

    private void cacheState(StationCoordinates coordinates, S state) {
        if (coordinates == null || state == null) {
            return;
        }
        runtimeStates.put(coordinates, state);
        dirtyStations.remove(coordinates);
    }

    protected final void removeState(StationCoordinates coordinates, boolean deleteFile) {
        if (coordinates == null) {
            return;
        }
        runtimeStates.remove(coordinates);
        dirtyStations.remove(coordinates);
        textDisplayService.removeStation(stationType(), coordinates);
        if (deleteFile) {
            stateStore.deleteAsync(coordinates);
        }
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
        }
    }

    protected final void appendLine(StringBuilder builder, String line) {
        if (Texts.isBlank(line)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    protected final String itemDisplayName(ItemStack itemStack) {
        String displayName = EmakiCoreLibApi.itemDisplayName(itemStack).orElse("");
        return Texts.isBlank(displayName)
                ? (itemStack == null || itemStack.getType() == null ? "" : itemStack.getType().name())
                : displayName;
    }
}
