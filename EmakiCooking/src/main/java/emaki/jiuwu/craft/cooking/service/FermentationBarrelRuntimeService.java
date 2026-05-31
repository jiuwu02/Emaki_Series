package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.List;
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
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class FermentationBarrelRuntimeService implements Listener {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final FermentationBarrelStateCodec codec = new FermentationBarrelStateCodec();
    private final FermentationBarrelTickProcessor tickProcessor = new FermentationBarrelTickProcessor();
    private final FermentationBarrelGuiController guiController;
    private final CookingTextDisplayService textDisplayService;
    private final Map<StationCoordinates, FermentationBarrelState> runtimeStates = new ConcurrentHashMap<>();
    private final Set<StationCoordinates> activeStations = ConcurrentHashMap.newKeySet();
    private BukkitTask tickerTask;

    public FermentationBarrelRuntimeService(EmakiCookingPlugin plugin, MessageService messageService, CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher, StationStateStore stateStore, CookingRecipeService recipeService,
            CookingRewardService rewardService, ItemSourceService itemSourceService,
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
        this.guiController = new FermentationBarrelGuiController(plugin, messageService, settingsService, itemSourceService, codec);
        this.guiController.setRuntimeService(this);
    }

    public void reload() {
        guiController.closeAllOpenInventories(false);
        cancelTicker();
        textDisplayService.removeStationType(StationType.FERMENTATION_BARREL);
        runtimeStates.clear();
        activeStations.clear();
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.FERMENTATION_BARREL).entrySet()) {
            StationCoordinates coordinates = entry.getKey();
            Block block = coordinates.block();
            FermentationBarrelState state = codec.readState(entry.getValue());
            if (block == null || !blockMatcher.matches(block, StationType.FERMENTATION_BARREL) || state.isCompletelyEmpty()) {
                removeState(coordinates, true);
                continue;
            }
            runtimeStates.put(coordinates, state);
            refreshText(coordinates, state);
            if (tickProcessor.shouldRemainActive(state)) {
                activeStations.add(coordinates);
            }
        }
        ensureTicker();
    }

    public void shutdown() {
        guiController.closeAllOpenInventories(false);
        flushAll();
        stateStore.waitForIdle().join();
        cancelTicker();
        textDisplayService.removeStationType(StationType.FERMENTATION_BARREL);
        runtimeStates.clear();
        activeStations.clear();
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand() || !blockMatcher.matches(block, StationType.FERMENTATION_BARREL)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        if (settingsService.matchesInteraction(StationType.FERMENTATION_BARREL, CookingSettingsService.INTERACTION_OPEN, interaction)) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.FERMENTATION_BARREL_USE) && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return guiController.openGui(player, coordinates);
        }
        if (settingsService.matchesInteraction(StationType.FERMENTATION_BARREL, CookingSettingsService.INTERACTION_START, interaction)) {
            return startOrCollect(player, block, coordinates, interaction);
        }
        if (settingsService.matchesInteraction(StationType.FERMENTATION_BARREL, CookingSettingsService.INTERACTION_INSPECT, interaction)) {
            interaction.cancel();
            return showInfo(player, coordinates);
        }
        return false;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(block, StationType.FERMENTATION_BARREL)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        FermentationBarrelGuiHolder openHolder = guiController.findOpenSession(coordinates);
        FermentationBarrelState state = openHolder == null ? loadStateOrEmpty(coordinates) : guiController.snapshotInventoryState(coordinates,
                openHolder.getInventory(), openHolder.viewerId(), Bukkit.getPlayer(openHolder.viewerId()) == null ? "" : Bukkit.getPlayer(openHolder.viewerId()).getName());
        if (state.isCompletelyEmpty()) {
            return false;
        }
        guiController.closeOpenInventories(coordinates, true);
        if (state.completed()) {
            dropResult(block, state);
        } else {
            dropOriginalItems(block, state);
        }
        removeState(coordinates, true);
        activeStations.remove(coordinates);
        return true;
    }

    private boolean startOrCollect(Player player, Block block, StationCoordinates coordinates, StationInteraction interaction) {
        FermentationBarrelState state = loadStateOrEmpty(coordinates);
        if (state.completed()) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.FERMENTATION_BARREL_COLLECT) && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            FermentationStage stage = currentFermentationStage(state, System.currentTimeMillis());
            deliverResult(player, block, state, stage);
            state.clearSlots();
            state.clearProcess();
            removeState(coordinates, true);
            activeStations.remove(coordinates);
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, collectionMessage(stage), Map.of());
            plugin.effectService().playActions(StationType.FERMENTATION_BARREL, "collect", player);
            return true;
        }
        if (state.fermenting()) {
            interaction.cancel();
            long now = System.currentTimeMillis();
            FermentationStage stage = currentFermentationStage(state, now);
            if (stage == FermentationStage.EARLY) {
                if (!player.hasPermission(CookingPermissions.FERMENTATION_BARREL_COLLECT) && !player.hasPermission(CookingPermissions.ADMIN)) {
                    messageService.send(player, "general.no_permission");
                    return true;
                }
                deliverResult(player, block, state, stage);
                state.clearSlots();
                state.clearProcess();
                removeState(coordinates, true);
                activeStations.remove(coordinates);
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, collectionMessage(stage), Map.of());
                plugin.effectService().playActions(StationType.FERMENTATION_BARREL, "collect", player);
                return true;
            }
            long seconds = Math.max(0L, (state.finishAtMs() - now) / 1000L);
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "fermentation_barrel.fermenting", Map.of("seconds", seconds));
            return true;
        }
        RecipeDocument recipe = findMatchingRecipe(state, player);
        if (recipe == null) {
            // 空桶或食材无法发酵：不拦截交互，放行让玩家可以破坏方块。
            return false;
        }
        interaction.cancel();
        if (!player.hasPermission(CookingPermissions.FERMENTATION_BARREL_START) && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            return true;
        }
        long now = System.currentTimeMillis();
        int seconds = Math.max(1, recipeService.fermentationTimeSeconds(recipe));
        state.setStartedAtMs(now);
        state.setFinishAtMs(now + seconds * 1000L);
        state.setFermenting(true);
        state.setCompleted(false);
        state.setActiveRecipeId(recipe.id());
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        activeStations.add(coordinates);
        ensureTicker();
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "fermentation_barrel.started", Map.of("seconds", seconds));
        plugin.effectService().playActions(StationType.FERMENTATION_BARREL, "start", player);
        return true;
    }

    private RecipeDocument findMatchingRecipe(FermentationBarrelState state, Player player) {
        Map<String, Integer> actual = aggregateActual(state);
        if (actual.isEmpty()) {
            return null;
        }
        for (RecipeDocument recipe : recipeService.fermentationBarrelRecipes()) {
            if (!recipeService.canUseRecipe(recipe, player)) {
                continue;
            }
            if (actual.equals(aggregateExpected(recipe))) {
                return recipe;
            }
        }
        return null;
    }

    private Map<String, Integer> aggregateActual(FermentationBarrelState state) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : state.slotSources().entrySet()) {
            result.merge(entry.getValue(), Math.max(1, state.slotAmounts().getOrDefault(entry.getKey(), 1)), Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> aggregateExpected(RecipeDocument recipe) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> input : recipeService.fermentationInputs(recipe)) {
            String source = firstSource(input.get("item_sources"));
            int amount = Math.max(1, CookingRuntimeUtil.parseInteger(input.get("amount"), 1));
            if (Texts.isNotBlank(source)) {
                result.merge(source, amount, Integer::sum);
            }
        }
        return result;
    }

    private String firstSource(Object raw) {
        ItemSource source = ItemSourceUtil.parse(raw);
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
    }

    private boolean showInfo(Player player, StationCoordinates coordinates) {
        FermentationBarrelState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        FermentationStage stage = currentFermentationStage(state, now);
        String status = statusText(state, stage);
        long seconds = state.fermenting() ? Math.max(0L, (state.finishAtMs() - now) / 1000L) : 0L;
        String progress = calculateProgress(state, now);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "fermentation_barrel.info", Map.of("status", status, "seconds", seconds, "progress", progress));
        return true;
    }

    private String statusText(FermentationBarrelState state, FermentationStage stage) {
        if (state == null || (!state.fermenting() && !state.completed())) {
            return messageService.message("fermentation_barrel.status_idle");
        }
        if (stage == FermentationStage.OVER) {
            return messageService.message("fermentation_barrel.status_over");
        }
        if (stage == FermentationStage.EARLY) {
            return messageService.message("fermentation_barrel.status_early_ready");
        }
        return state.completed() ? messageService.message("fermentation_barrel.status_completed")
                : messageService.message("fermentation_barrel.status_fermenting");
    }

    private FermentationStage currentFermentationStage(FermentationBarrelState state, long now) {
        if (state == null) {
            return FermentationStage.COMPLETE;
        }
        RecipeDocument recipe = recipeService.fermentationBarrelRecipeById(state.activeRecipeId());
        if (recipe == null) {
            return FermentationStage.COMPLETE;
        }
        if (state.completed()) {
            int overSeconds = recipeService.fermentationOverTimeSeconds(recipe);
            if (overSeconds > 0 && now >= state.finishAtMs() + overSeconds * 1000L) {
                return FermentationStage.OVER;
            }
            return FermentationStage.COMPLETE;
        }
        if (state.fermenting()) {
            double earlyRatio = recipeService.fermentationEarlyMinProgressRatio(recipe);
            if (earlyRatio >= 0.0D) {
                long total = Math.max(1L, state.finishAtMs() - state.startedAtMs());
                long done = Math.max(0L, now - state.startedAtMs());
                if ((double) done / (double) total >= earlyRatio) {
                    return FermentationStage.EARLY;
                }
            }
        }
        return FermentationStage.COMPLETE;
    }

    private String collectionMessage(FermentationStage stage) {
        if (stage == FermentationStage.EARLY) {
            return "fermentation_barrel.collected_early";
        }
        if (stage == FermentationStage.OVER) {
            return "fermentation_barrel.collected_over";
        }
        return "fermentation_barrel.collected";
    }

    private String calculateProgress(FermentationBarrelState state, long now) {
        if (!state.fermenting() && !state.completed()) {
            return "0.00%";
        }
        if (state.completed()) {
            return "100.00%";
        }
        long total = Math.max(1L, state.finishAtMs() - state.startedAtMs());
        long done = Math.max(0L, now - state.startedAtMs());
        return String.format(java.util.Locale.ROOT, "%.2f%%", Math.min(100D, (double) done * 100D / (double) total));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (StationCoordinates coordinates : List.copyOf(activeStations)) {
            FermentationBarrelState state = loadStateOrEmpty(coordinates);
            Block block = coordinates.block();
            if (block == null || !blockMatcher.matches(block, StationType.FERMENTATION_BARREL)) {
                removeState(coordinates, true);
                activeStations.remove(coordinates);
                continue;
            }
            boolean changed = tickProcessor.process(state, now, settingsService.fermentationBarrelPauseWhenOpen() && guiController.hasOpenSession(coordinates));
            if (changed) {
                saveState(coordinates, state);
            } else {
                refreshText(coordinates, state);
            }
            if (!tickProcessor.shouldRemainActive(state)) {
                activeStations.remove(coordinates);
            }
        }
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    private void ensureTicker() {
        if (activeStations.isEmpty() || (tickerTask != null && !tickerTask.isCancelled())) {
            return;
        }
        tickerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void deliverResult(Player player, Block block, FermentationBarrelState state, FermentationStage stage) {
        RecipeDocument recipe = recipeService.fermentationBarrelRecipeById(state.activeRecipeId());
        if (recipe == null) {
            return;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        Map<String, Object> outcome = recipeService.fermentationOutcomeForStage(recipe, stage);
        rewardService.deliver(recipe, player, location, settingsService.fermentationBarrelDropResult(), recipeService.outputs(outcome),
                recipeService.actions(outcome), "cooking_fermentation_barrel_complete", Map.of("recipe_id", recipe.id(), "station_type", StationType.FERMENTATION_BARREL.folderName(), "stage", stage.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void dropResult(Block block, FermentationBarrelState state) { deliverResult(null, block, state, currentFermentationStage(state, System.currentTimeMillis())); }

    private void dropOriginalItems(Block block, FermentationBarrelState state) {
        if (block.getWorld() == null) {
            return;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            ItemStack item = codec.deserializeItem(state.slotItemData(entry.getKey()));
            if (item == null || item.getType().isAir()) {
                ItemSource source = ItemSourceUtil.parse(entry.getValue());
                item = source == null ? null : itemSourceService.createItem(source, state.slotAmounts().getOrDefault(entry.getKey(), 1));
            }
            if (item != null && !item.getType().isAir()) {
                block.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    FermentationBarrelState saveInventory(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        FermentationBarrelState state = guiController.snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
        saveState(coordinates, state);
        return state;
    }

    void saveState(StationCoordinates coordinates, FermentationBarrelState state) {
        if (coordinates == null || state == null || state.isCompletelyEmpty()) {
            removeState(coordinates, true);
            return;
        }
        runtimeStates.put(coordinates, state);
        stateStore.saveAsync(coordinates, codec.serializeState(coordinates, state));
        refreshText(coordinates, state);
    }

    FermentationBarrelState loadStateOrEmpty(StationCoordinates coordinates) {
        FermentationBarrelState cached = runtimeStates.get(coordinates);
        if (cached != null) {
            return cached;
        }
        FermentationBarrelState loaded = codec.readState(stateStore.load(coordinates));
        runtimeStates.putIfAbsent(coordinates, loaded);
        return loaded;
    }

    void removeState(StationCoordinates coordinates, boolean deleteFile) {
        runtimeStates.remove(coordinates);
        if (coordinates != null) {
            textDisplayService.removeStation(StationType.FERMENTATION_BARREL, coordinates);
        }
        if (deleteFile) {
            stateStore.deleteAsync(coordinates);
        }
    }

    private void refreshText(StationCoordinates coordinates, FermentationBarrelState state) {
        if (!settingsService.textDisplayEnabled(StationType.FERMENTATION_BARREL) || coordinates == null
                || state == null || (!state.fermenting() && !state.completed())) {
            textDisplayService.removeStation(StationType.FERMENTATION_BARREL, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.FERMENTATION_BARREL, coordinates);
            return;
        }
        long now = System.currentTimeMillis();
        FermentationStage stage = currentFermentationStage(state, now);
        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.fermentation_barrel.title"));
        RecipeDocument recipe = recipeService.fermentationBarrelRecipeById(state.activeRecipeId());
        if (recipe != null) {
            appendLine(builder, messageService.message("text_display.fermentation_barrel.recipe", Map.of("recipe", recipe.displayName())));
        }
        if (state.completed()) {
            appendLine(builder, messageService.message(stage == FermentationStage.OVER
                    ? "text_display.fermentation_barrel.over"
                    : "text_display.fermentation_barrel.ready"));
        } else if (stage == FermentationStage.EARLY) {
            appendLine(builder, messageService.message("text_display.fermentation_barrel.early_ready"));
        } else {
            long seconds = Math.max(0L, (state.finishAtMs() - now) / 1000L);
            appendLine(builder, messageService.message("text_display.fermentation_barrel.fermenting", Map.of("seconds", seconds)));
        }
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.FERMENTATION_BARREL,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.FERMENTATION_BARREL)
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

    private void flushAll() {
        for (Map.Entry<StationCoordinates, FermentationBarrelState> entry : runtimeStates.entrySet()) {
            if (!entry.getValue().isCompletelyEmpty()) {
                stateStore.saveAsync(entry.getKey(), codec.serializeState(entry.getKey(), entry.getValue()));
            }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { guiController.onInventoryClose(event); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { guiController.onInventoryClick(event); }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { guiController.onInventoryDrag(event); }
}
