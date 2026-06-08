package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
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

public final class SteamerRuntimeService implements Listener {

    private static final long DIRTY_FLUSH_INTERVAL_TICKS = 100L;

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final SteamerStateCodec codec;
    private final SteamerTickProcessor tickProcessor;
    private final SteamerGuiController guiController;
    private final CookingTextDisplayService textDisplayService;
    private final Map<StationCoordinates, SteamerState> runtimeStates = new ConcurrentHashMap<>();
    private final Set<StationCoordinates> activeStations = ConcurrentHashMap.newKeySet();
    private final Set<StationCoordinates> dirtyStations = ConcurrentHashMap.newKeySet();
    private TaskHandle tickerTask;
    private TaskHandle flushTask;

    public SteamerRuntimeService(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            CookingTextDisplayService textDisplayService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.recipeService = recipeService;
        this.rewardService = rewardService;
        this.itemSourceService = itemSourceService;
        this.textDisplayService = textDisplayService;
        this.codec = new SteamerStateCodec();
        this.tickProcessor = new SteamerTickProcessor(settingsService, blockMatcher, recipeService, rewardService, itemSourceService, codec);
        this.guiController = new SteamerGuiController(plugin, messageService, settingsService, itemSourceService, recipeService, codec);
        this.guiController.setRuntimeService(this);
    }

    public SteamerGuiController guiController() {
        return guiController;
    }

    SteamerTickProcessor tickProcessor() {
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
        textDisplayService.removeStationType(StationType.STEAMER);
        activeStations.clear();
        runtimeStates.clear();
        dirtyStations.clear();
        long now = System.currentTimeMillis();
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.STEAMER).entrySet()) {
            StationCoordinates coordinates = entry.getKey();
            SteamerState state = codec.readState(entry.getValue());
            ItemSource stationSource = stateStore.stationSource(entry.getValue());
            Block block = coordinates.block();
            if (state == null || !blockMatcher.matches(block, StationType.STEAMER, stationSource)) {
                guiController.closeOpenInventories(coordinates, true);
                removeState(coordinates, true);
                continue;
            }
            cacheState(coordinates, state);
            refreshText(coordinates, state);
            if (tickProcessor.shouldRemainActive(state, now)) {
                activeStations.add(coordinates);
            }
        }
        ensureTicker();
    }

    public void shutdown() {
        guiController.closeAllOpenInventories(false);
        flushDirtyStates();
        stateStore.waitForIdle().join();
        cancelFlushTask();
        cancelTicker();
        textDisplayService.removeStationType(StationType.STEAMER);
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
        if (blockMatcher.matches(interaction, StationType.STEAMER)) {
            return handleSteamerBlockInteraction(interaction, block, player);
        }

        if (!tickProcessor.isHeatSourceBlock(block)) {
            return false;
        }
        Block topBlock = block.getRelative(BlockFace.UP);
        if (!blockMatcher.matches(topBlock, StationType.STEAMER)) {
            return false;
        }
        return handleHeatSourceBlockInteraction(interaction, block, topBlock, player);
    }

    private boolean handleSteamerBlockInteraction(StationInteraction interaction, Block steamerBlock, Player player) {
        Block heatSourceBlock = steamerBlock.getRelative(BlockFace.DOWN);
        boolean hasHeatSource = tickProcessor.isHeatSourceBlock(heatSourceBlock);

        StationCoordinates coordinates = StationCoordinates.fromBlock(steamerBlock);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hasHeatSource && handleResourceInput(interaction, player, coordinates, heatSourceBlock, hand)) {
            return true;
        }

        if (!settingsService.matchesInteraction(
                StationType.STEAMER,
                CookingSettingsService.INTERACTION_OPEN,
                interaction)) {
            return false;
        }
        if (!hasHeatSource) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.no_heat_source", Map.of());
            interaction.cancel();
            return true;
        }
        if (!player.hasPermission(CookingPermissions.STEAMER_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        interaction.cancel();
        return guiController.openGui(player, coordinates);
    }

    private boolean handleHeatSourceBlockInteraction(StationInteraction interaction,
            Block heatSourceBlock,
            Block steamerBlock,
            Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        StationCoordinates coordinates = StationCoordinates.fromBlock(steamerBlock);
        if (handleResourceInput(interaction, player, coordinates, heatSourceBlock, hand)) {
            return true;
        }
        if (!settingsService.matchesInteraction(
                StationType.STEAMER,
                CookingSettingsService.INTERACTION_OPEN,
                interaction)) {
            return false;
        }
        if (hand == null || hand.getType().isAir()) {
            if (!player.hasPermission(CookingPermissions.STEAMER_USE)
                    && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                interaction.cancel();
                return true;
            }
            interaction.cancel();
            return guiController.openGui(player, coordinates);
        }
        return false;
    }

    private boolean handleResourceInput(StationInteraction interaction,
            Player player,
            StationCoordinates coordinates,
            Block heatSourceBlock,
            ItemStack hand) {
        CookingSettingsService.SteamerMoistureRule moistureRule = matchMoistureRule(hand);
        if (moistureRule != null && settingsService.matchesInteraction(
                StationType.STEAMER,
                CookingSettingsService.INTERACTION_MOISTURE,
                interaction)) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.STEAMER_MOISTURE)
                    && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return addMoisture(player, coordinates, hand, moistureRule);
        }

        CookingSettingsService.SteamerFuelRule fuelRule = matchFuelRule(hand);
        if (fuelRule != null && settingsService.matchesInteraction(
                StationType.STEAMER,
                CookingSettingsService.INTERACTION_FUEL,
                interaction)) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.STEAMER_FUEL)
                    && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return addFuel(player, coordinates, heatSourceBlock, hand, fuelRule);
        }
        return false;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null) {
            return false;
        }
        Block steamerBlock = null;
        if (blockMatcher.matches(context, StationType.STEAMER)) {
            steamerBlock = block;
        } else if (tickProcessor.isHeatSourceBlock(block) && blockMatcher.matches(block.getRelative(BlockFace.UP), StationType.STEAMER)) {
            steamerBlock = block.getRelative(BlockFace.UP);
        }
        if (steamerBlock == null) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(steamerBlock);
        stateStore.rememberStationSource(coordinates, context.stationSource());
        SteamerGuiHolder openHolder = guiController.findOpenSession(coordinates);
        SteamerState state = openHolder == null
                ? loadStateOrEmpty(coordinates)
                : guiController.snapshotInventoryState(
                        coordinates,
                        openHolder.getInventory(),
                        openHolder.viewerId(),
                        Bukkit.getPlayer(openHolder.viewerId()) == null ? "" : Bukkit.getPlayer(openHolder.viewerId()).getName()
                );
        if (state.isCompletelyEmpty()) {
            return false;
        }
        guiController.closeOpenInventories(coordinates, true);
        tickProcessor.extinguishHeatSource(steamerBlock.getRelative(BlockFace.DOWN));
        tickProcessor.dropStoredItems(steamerBlock, state);
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
            Block heatSourceBlock,
            ItemStack hand,
            CookingSettingsService.SteamerFuelRule rule) {
        if (player == null || coordinates == null || hand == null || rule == null) {
            return false;
        }
        SteamerState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long currentBurning = state.burningUntilMs();
        long durationMs = Math.max(0L, rule.durationSeconds()) * 1000L;
        long newBurning = currentBurning > now ? currentBurning + durationMs : now + durationMs;
        state.setBurningUntilMs(newBurning);
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        CookingRuntimeUtil.takeOneFromMainHand(player);
        if (settingsService.steamerIgniteHeatSource()) {
            tickProcessor.igniteHeatSource(heatSourceBlock, newBurning, now);
        }
        activeStations.add(coordinates);
        ensureTicker();
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.add_fuel", Map.of(
                "item", itemDisplayName(hand),
                "seconds", Math.max(0L, (newBurning - now) / 1000L)
        ));
        plugin.effectService().playActions(StationType.STEAMER, "fuel", player);
        return true;
    }

    private boolean addMoisture(Player player,
            StationCoordinates coordinates,
            ItemStack hand,
            CookingSettingsService.SteamerMoistureRule rule) {
        if (player == null || coordinates == null || hand == null || rule == null) {
            return false;
        }
        SteamerState state = loadStateOrEmpty(coordinates);
        state.setMoisture(state.moisture() + Math.max(0, rule.moisture()));
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        CookingRuntimeUtil.takeOneFromMainHand(player);
        if (rule.outputSource() != null) {
            ItemStack output = itemSourceService.createItem(rule.outputSource(), 1);
            if (output != null && !output.getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, output);
            }
        }
        if (state.moisture() > 0) {
            activeStations.add(coordinates);
            ensureTicker();
        }
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.add_moisture", Map.of(
                "item", itemDisplayName(hand),
                "moisture", Math.max(0, rule.moisture()),
                "total", state.moisture()
        ));
        plugin.effectService().playActions(StationType.STEAMER, "moisture", player);
        return true;
    }

    private boolean showInfo(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            return false;
        }
        SteamerState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long remainingBurnTime = state.burningUntilMs() > now ? (state.burningUntilMs() - now) / 1000L : 0L;
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.info", Map.of(
                "burning_time", remainingBurnTime,
                "moisture", state.moisture(),
                "steam", state.steam(),
                "progress", calculateProgressStatus(state)
        ));
        return true;
    }

    private String calculateProgressStatus(SteamerState state) {
        if (state == null || state.slotSources().isEmpty()) {
            return messageService.message("steamer.progress_not_started");
        }
        int totalRequired = 0;
        int totalProgress = 0;
        int validIngredients = 0;
        boolean allCompleted = true;
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            RecipeDocument recipe = recipeService.findSteamerRecipe(entry.getValue(), null);
            if (recipe == null) {
                continue;
            }
            int required = recipeService.steamerRequiredSteam(recipe);
            int progress = Math.min(required, state.progressAt(entry.getKey()));
            validIngredients++;
            totalRequired += required;
            totalProgress += progress;
            if (progress < required) {
                allCompleted = false;
            }
        }
        if (validIngredients <= 0) {
            return messageService.message("steamer.progress_not_started");
        }
        if (allCompleted) {
            return messageService.message("steamer.progress_completed");
        }
        if (totalRequired <= 0) {
            return "0.00%";
        }
        return String.format(Locale.ROOT, "%.2f%%", (double) totalProgress * 100.0D / (double) totalRequired);
    }

    private void refreshText(StationCoordinates coordinates, SteamerState state) {
        if (!settingsService.textDisplayEnabled(StationType.STEAMER) || coordinates == null
                || state == null || state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.STEAMER, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.STEAMER, coordinates);
            return;
        }
        long now = System.currentTimeMillis();
        long remainingBurn = state.burningUntilMs() > now ? (state.burningUntilMs() - now) / 1000L : 0L;
        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.steamer.title"));
        appendLine(builder, messageService.message("text_display.steamer.burning", Map.of("burning_time", remainingBurn)));
        appendLine(builder, messageService.message("text_display.steamer.moisture", Map.of("moisture", state.moisture())));
        appendLine(builder, messageService.message("text_display.steamer.steam", Map.of("steam", state.steam())));
        if (state.slotSources().isEmpty()) {
            appendLine(builder, messageService.message("text_display.steamer.idle"));
        } else {
            appendLine(builder, messageService.message("text_display.steamer.progress", Map.of("progress", calculateProgressStatus(state))));
        }
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.STEAMER,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.STEAMER)
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

    void ensureTicker() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        if (tickerTask != null && !FoliaSchedulerAdapter.isTaskCancelled(tickerTask)) {
            return;
        }
        tickerTask = FoliaSchedulerAdapter.runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void ensureFlushTask() {
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
            return;
        }
        if (flushTask != null && !FoliaSchedulerAdapter.isTaskCancelled(flushTask)) {
            return;
        }
        flushTask = FoliaSchedulerAdapter.runTaskTimer(
                plugin,
                this::flushDirtyStates,
                DIRTY_FLUSH_INTERVAL_TICKS,
                DIRTY_FLUSH_INTERVAL_TICKS
        );
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            FoliaSchedulerAdapter.cancelTask(tickerTask);
            tickerTask = null;
        }
    }

    private void cancelFlushTask() {
        if (flushTask != null) {
            FoliaSchedulerAdapter.cancelTask(flushTask);
            flushTask = null;
        }
    }

    private void flushDirtyStates() {
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
            return;
        }
        for (StationCoordinates coordinates : List.copyOf(dirtyStations)) {
            SteamerState state = runtimeStates.get(coordinates);
            if (state == null || state.isCompletelyEmpty()) {
                removeState(coordinates, true);
                continue;
            }
            Map<String, Object> serialized = codec.serializeState(coordinates, state);
            dirtyStations.remove(coordinates);
            stateStore.saveAsync(coordinates, serialized).thenAccept(success -> {
                if (!success) {
                    dirtyStations.add(coordinates);
                }
            });
        }
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
        }
    }

    private void tick() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        long now = System.currentTimeMillis();
        for (StationCoordinates coordinates : List.copyOf(activeStations)) {
            processStation(coordinates, now);
        }
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    private void processStation(StationCoordinates coordinates, long now) {
        Block block = coordinates == null ? null : coordinates.block();
        SteamerState state = loadStateOrEmpty(coordinates);
        ItemSource stationSource = stateStore.rememberedStationSource(coordinates);
        if (block == null || !blockMatcher.matches(block, StationType.STEAMER, stationSource)) {
            guiController.closeOpenInventories(coordinates, true);
            removeState(coordinates, true);
            activeStations.remove(coordinates);
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

    SteamerState saveInventory(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        if (coordinates == null || inventory == null) {
            return new SteamerState();
        }
        SteamerState updated = guiController.snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
        saveState(coordinates, updated);
        return updated;
    }

    void saveState(StationCoordinates coordinates, SteamerState state) {
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

    SteamerState loadStateOrEmpty(StationCoordinates coordinates) {
        if (coordinates == null) {
            return new SteamerState();
        }
        SteamerState cached = runtimeStates.get(coordinates);
        if (cached != null) {
            return cached;
        }
        SteamerState loaded = codec.readState(stateStore.load(coordinates));
        SteamerState existing = runtimeStates.putIfAbsent(coordinates, loaded);
        return existing == null ? loaded : existing;
    }

    private void cacheState(StationCoordinates coordinates, SteamerState state) {
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
        textDisplayService.removeStation(StationType.STEAMER, coordinates);
        if (deleteFile) {
            stateStore.deleteAsync(coordinates);
        }
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
        }
    }

    private CookingSettingsService.SteamerFuelRule matchFuelRule(ItemStack itemStack) {
        ItemSource identified = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        if (identified == null) {
            return null;
        }
        for (CookingSettingsService.SteamerFuelRule rule : settingsService.steamerFuels()) {
            if (rule != null && ItemSourceUtil.matches(rule.source(), identified)) {
                return rule;
            }
        }
        return null;
    }

    private CookingSettingsService.SteamerMoistureRule matchMoistureRule(ItemStack itemStack) {
        ItemSource identified = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        if (identified == null) {
            return null;
        }
        for (CookingSettingsService.SteamerMoistureRule rule : settingsService.steamerMoistureSources()) {
            if (rule != null && ItemSourceUtil.matches(rule.inputSource(), identified)) {
                return rule;
            }
        }
        return null;
    }

    private String itemDisplayName(ItemStack itemStack) {
        String displayName = EmakiCoreLibApi.itemDisplayName(itemStack);
        return Texts.isBlank(displayName)
                ? (itemStack == null || itemStack.getType() == null ? "" : itemStack.getType().name())
                : displayName;
    }
}
