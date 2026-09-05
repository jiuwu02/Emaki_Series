package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
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
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class FermentationBarrelRuntimeService implements Listener {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingCompletionCoordinator completionCoordinator;
    private final EmakiScheduling taskScheduler;
    private final ItemSourceService itemSourceService;
    private final FermentationBarrelStateCodec codec = new FermentationBarrelStateCodec();
    private final FermentationBarrelTickProcessor tickProcessor = new FermentationBarrelTickProcessor();
    private final FermentationBarrelGuiController guiController;
    private final CookingTextDisplayService textDisplayService;
    private final Map<StationCoordinates, FermentationBarrelState> runtimeStates = new ConcurrentHashMap<>();
    private final Set<StationCoordinates> activeStations = ConcurrentHashMap.newKeySet();
    private final Set<StationCoordinates> tickingStations = ConcurrentHashMap.newKeySet();
    private TaskToken tickerTask;

    public FermentationBarrelRuntimeService(EmakiCookingPlugin plugin, MessageService messageService, CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher, StationStateStore stateStore, CookingRecipeService recipeService,
            CookingCompletionCoordinator completionCoordinator,
            ItemSourceService itemSourceService, CookingTextDisplayService textDisplayService,
            EmakiScheduling taskScheduler) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.recipeService = recipeService;
        this.completionCoordinator = completionCoordinator;
        this.taskScheduler = taskScheduler;
        if (completionCoordinator != null) {
            completionCoordinator.register(completionStateAccess());
        }
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
        stateStore.forEachLoadedState(StationType.FERMENTATION_BARREL, this::restoreStoredState);
        ensureTicker();
    }

    CookingStationStateAccess completionStateAccess() {
        return new CookingStationStateAccess() {
            @Override
            public StationType stationType() {
                return StationType.FERMENTATION_BARREL;
            }

            @Override
            public Map<String, Object> snapshot(StationCoordinates coordinates) {
                FermentationBarrelState state = loadStateOrEmpty(coordinates);
                return state == null || !state.valid() || !state.slotIdsResolved() || state.isCompletelyEmpty()
                        ? null : codec.serializeState(coordinates, state);
            }

            @Override
            public CompletionStage<Void> replace(StationCoordinates coordinates, Map<String, Object> committedState) {
                FermentationBarrelState state = codec.readState(new MapYamlSection(committedState));
                if (state == null || !state.valid() || !state.slotIdsResolved() || state.isCompletelyEmpty()) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid committed fermentation barrel state"));
                }
                return stateStore.saveAsync(coordinates, committedState)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            runtimeStates.put(coordinates, state);
                            if (state.fermenting() || state.completed()) {
                                activeStations.add(coordinates);
                                ensureTicker();
                            } else {
                                activeStations.remove(coordinates);
                            }
                            refreshText(coordinates, state);
                        }));
            }

            @Override
            public CompletionStage<Void> delete(StationCoordinates coordinates) {
                return stateStore.deleteAsync(coordinates)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            removeState(coordinates, false);
                            activeStations.remove(coordinates);
                            if (activeStations.isEmpty()) {
                                cancelTicker();
                            }
                        }));
            }
        };
    }

    public boolean restoreStoredState(StationCoordinates coordinates, YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        Block block = coordinates.block();
        FermentationBarrelState state = codec.readState(section);
        ItemSourceRef stationSource = stateStore.stationSource(section);
        boolean needsCanonicalWriteback = state != null && (state.needsSchemaWriteback() || !state.slotIdsResolved());
        if (state == null || !state.valid()) {
            plugin.getLogger().warning("Station restore report: rejected_invalid_fermentation_barrel_state coordinate=" + coordinates.runtimeKey());
            return false;
        }
        if (!migrateSlotIds(state)) {
            plugin.getLogger().warning("Station restore report: rejected_fermentation_barrel_slot_id_migration coordinate=" + coordinates.runtimeKey()
                    + " recipe=" + state.activeRecipeId());
            return false;
        }
        if (state.isCompletelyEmpty()) {
            removeState(coordinates, false);
            activeStations.remove(coordinates);
            return false;
        }
        if (!blockMatcher.matches(block, StationType.FERMENTATION_BARREL, stationSource)) {
            removeState(coordinates, false);
            activeStations.remove(coordinates);
            plugin.getLogger().warning("Station restore report: skipped_mismatch type=fermentation_barrel coordinate=" + coordinates.runtimeKey());
            return false;
        }
        runtimeStates.put(coordinates, state);
        if (needsCanonicalWriteback) {
            state.markSchemaCurrent();
            saveState(coordinates, state);
        }
        refreshText(coordinates, state);
        if (state.slotIdsResolved() && tickProcessor.shouldRemainActive(state)) {
            activeStations.add(coordinates);
        }
        ensureTicker();
        return true;
    }

    public void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        FermentationBarrelState state = runtimeStates.get(coordinates);
        if (state != null && state.valid() && state.slotIdsResolved() && !state.isCompletelyEmpty()) {
            stateStore.save(coordinates, codec.serializeState(coordinates, state));
        }
        removeState(coordinates, false);
        activeStations.remove(coordinates);
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    public void shutdown() {
        guiController.closeAllOpenInventories(false);
        cancelTicker();
        waitForInFlightTicks();
        flushAll();
        textDisplayService.removeStationType(StationType.FERMENTATION_BARREL);
        runtimeStates.clear();
        activeStations.clear();
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand() || !blockMatcher.matches(interaction, StationType.FERMENTATION_BARREL)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.FERMENTATION_BARREL, coordinates)) {
            interaction.cancel();
            return true;
        }
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
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
        if (block == null || !blockMatcher.matches(context, StationType.FERMENTATION_BARREL)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.FERMENTATION_BARREL, coordinates)) {
            context.cancel();
            return true;
        }
        stateStore.rememberStationSource(coordinates, context.stationSource());
        FermentationBarrelGuiHolder openHolder = guiController.findOpenSession(coordinates);
        FermentationBarrelState state = openHolder == null ? loadStateOrEmpty(coordinates) : guiController.snapshotInventoryState(coordinates,
                openHolder.getInventory(), openHolder.viewerId(), Bukkit.getPlayer(openHolder.viewerId()) == null ? "" : Bukkit.getPlayer(openHolder.viewerId()).getName());
        if (state == null || !state.valid() || !state.slotIdsResolved()) {
            context.cancel();
            plugin.getLogger().warning("Station break report: rejected_fermentation_barrel_slot_id_migration coordinate=" + coordinates.runtimeKey());
            return true;
        }
        if (state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.FERMENTATION_BARREL, coordinates);
            return false;
        }
        guiController.closeOpenInventories(coordinates, true);
        if (state.completed()) {
            submitCompletion(null, block, coordinates, state, currentFermentationStage(state, System.currentTimeMillis()), true, false);
            return true;
        }
        dropOriginalItems(block, state);
        removeState(coordinates, true);
        activeStations.remove(coordinates);
        return true;
    }

    private boolean startOrCollect(Player player, Block block, StationCoordinates coordinates, StationInteraction interaction) {
        FermentationBarrelState state = loadStateOrEmpty(coordinates);
        if (state == null || !state.valid() || !state.slotIdsResolved()) {
            interaction.cancel();
            plugin.getLogger().warning("Station interaction report: rejected_fermentation_barrel_slot_id_migration coordinate=" + coordinates.runtimeKey());
            return true;
        }
        if (state.completed()) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.FERMENTATION_BARREL_COLLECT) && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            FermentationStage stage = currentFermentationStage(state, System.currentTimeMillis());
            submitCompletion(player, block, coordinates, state, stage, settingsService.fermentationBarrelDropResult(), true);
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
                submitCompletion(player, block, coordinates, state, stage, settingsService.fermentationBarrelDropResult(), true);
                return true;
            }
            long seconds = Math.max(0L, (state.finishAtMs() - now) / 1000L);
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "fermentation_barrel.fermenting", Map.of("seconds", seconds));
            return true;
        }
        RecipeDocument recipe = findMatchingRecipe(state, player);
        if (recipe == null) {
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
        if (state == null || !state.valid() || !state.slotIdsResolved()) {
            return null;
        }
        Map<String, Integer> actual = aggregateActual(state);
        if (actual.isEmpty()) {
            return null;
        }
        for (RecipeDocument recipe : recipeService.fermentationBarrelRecipes()) {
            if (!recipeService.canUseRecipe(recipe, player) || invalidFermentationIdentities(recipe)) {
                continue;
            }
            if (actual.equals(aggregateExpected(recipe)) && matchesInputMatchers(recipe, state, player)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean matchesInputMatchers(RecipeDocument recipe, FermentationBarrelState state, Player player) {
        for (Map<String, Object> input : recipeService.fermentationInputs(recipe)) {
            ItemRequirement requirement = CookingMatchers.requirement(input, "item_sources", "matcher");
            if (requirement.empty()) {
                return false;
            }
            String expectedSlotId = inputSlotId(input);
            if (!matchesSlotWithId(state, expectedSlotId, requirement, player)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSlotWithId(FermentationBarrelState state,
            String expectedSlotId,
            ItemRequirement requirement,
            Player player) {
        boolean matched = false;
        for (Map.Entry<Integer, String> entry : state.slotIds().entrySet()) {
            if (Texts.isNotBlank(expectedSlotId) && !expectedSlotId.equalsIgnoreCase(entry.getValue())) {
                continue;
            }
            ItemStack stored = StoredItemCodec.deserialize(state.slotItemData(entry.getKey()));
            if (stored == null || stored.getType().isAir()) {
                continue;
            }
            if (!requirement.test(stored, ItemSourceUtil.parse(state.slotSources().get(entry.getKey())), player)) {
                return false;
            }
            matched = true;
        }
        return matched;
    }

    private Map<String, Integer> aggregateActual(FermentationBarrelState state) {
        return FermentationIdentityResolver.aggregate(state.slotCountKeys(), state.slotAmounts());
    }

    private Map<String, Integer> aggregateExpected(RecipeDocument recipe) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> input : recipeService.fermentationInputs(recipe)) {
            String countKey = inputCountKey(input);
            int amount = Math.max(1, CookingRuntimeUtil.parseInteger(input.get("amount"), 1));
            if (Texts.isNotBlank(countKey)) {
                result.merge(countKey, amount, Integer::sum);
            }
        }
        return result;
    }

    String resolveSlotId(ItemStack item, String source, Player player) {
        if (item == null || item.getType().isAir() || Texts.isBlank(source)) {
            return "";
        }
        ItemSourceRef itemSource = ItemSourceUtil.parse(source);
        Set<String> candidates = new LinkedHashSet<>();
        for (RecipeDocument recipe : recipeService.fermentationBarrelRecipes()) {
            if (!recipeService.canUseRecipe(recipe, player) || invalidFermentationIdentities(recipe)) {
                continue;
            }
            for (Map<String, Object> input : recipeService.fermentationInputs(recipe)) {
                ItemRequirement requirement = CookingMatchers.requirement(input, "item_sources", "matcher");
                if (requirement.empty() || !requirement.test(item, itemSource, player)) {
                    continue;
                }
                String slotId = inputSlotId(input);
                if (Texts.isNotBlank(slotId)) {
                    candidates.add(slotId);
                }
            }
        }
        if (candidates.size() != 1) {
            return "";
        }
        return candidates.iterator().next();
    }

    String resolveCountKey(ItemStack item, String slotId, String source, Player player) {
        if (item == null || item.getType().isAir() || Texts.isBlank(slotId) || Texts.isBlank(source)) {
            return "";
        }
        ItemSourceRef itemSource = ItemSourceUtil.parse(source);
        Set<String> candidates = new LinkedHashSet<>();
        for (RecipeDocument recipe : recipeService.fermentationBarrelRecipes()) {
            if (!recipeService.canUseRecipe(recipe, player)) {
                continue;
            }
            for (Map<String, Object> input : recipeService.fermentationInputs(recipe)) {
                if (!slotId.equalsIgnoreCase(inputSlotId(input))) {
                    continue;
                }
                ItemRequirement requirement = CookingMatchers.requirement(input, "item_sources", "matcher");
                if (requirement.empty() || !requirement.test(item, itemSource, player)) {
                    continue;
                }
                String countKey = inputCountKey(input);
                if (Texts.isNotBlank(countKey)) {
                    candidates.add(countKey);
                }
            }
        }
        return candidates.size() == 1 ? candidates.iterator().next() : "";
    }

    private String inputSlotId(Map<String, Object> input) {
        String declared = Texts.toStringSafe(input == null ? null : input.get("slot_id")).trim();
        return Texts.isNotBlank(declared) ? declared.toLowerCase(Locale.ROOT) : "";
    }

    private String inputCountKey(Map<String, Object> input) {
        String declared = Texts.toStringSafe(input == null ? null : input.get("count_key")).trim();
        return Texts.isNotBlank(declared) ? declared.toLowerCase(Locale.ROOT) : "";
    }

    private boolean migrateSlotIds(FermentationBarrelState state) {
        if (state == null || state.slotIdsResolved()) {
            return state != null && state.valid();
        }
        if (!state.valid() || Texts.isBlank(state.activeRecipeId())) {
            if (state != null) {
                state.markSlotIdMigrationFailed();
            }
            return false;
        }
        RecipeDocument activeRecipe = recipeService.fermentationBarrelRecipeById(state.activeRecipeId());
        if (activeRecipe == null || invalidFermentationIdentities(activeRecipe)) {
            state.markSlotIdMigrationFailed();
            return false;
        }
        Map<Integer, List<FermentationIdentityResolver.Identity>> candidatesBySlot = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : state.slotSources().entrySet()) {
            ItemStack stored = StoredItemCodec.deserialize(state.slotItemData(entry.getKey()));
            if (stored == null || stored.getType().isAir()) {
                state.markSlotIdMigrationFailed();
                return false;
            }
            ItemSourceRef source = ItemSourceUtil.parse(entry.getValue());
            List<FermentationIdentityResolver.Identity> candidates = new ArrayList<>();
            for (Map<String, Object> input : recipeService.fermentationInputs(activeRecipe)) {
                ItemRequirement requirement = CookingMatchers.requirement(input, "item_sources", "matcher");
                if (requirement.empty() || !requirement.test(stored, source, null)) {
                    continue;
                }
                candidates.add(new FermentationIdentityResolver.Identity(inputSlotId(input), inputCountKey(input)));
            }
            candidatesBySlot.put(entry.getKey(), candidates);
        }
        FermentationIdentityResolver.MigrationResult migration = FermentationIdentityResolver.migrate(candidatesBySlot);
        if (!migration.accepted()) {
            state.markSlotIdMigrationFailed();
            return false;
        }
        for (Map.Entry<Integer, FermentationIdentityResolver.Identity> entry : migration.allocations().entrySet()) {
            state.replaceSlotIdentity(entry.getKey(), entry.getValue().slotId(), entry.getValue().countKey());
        }
        state.markSlotIdsResolved();
        return true;
    }

    private CookingInputIngredient fermentationInputBySlotId(RecipeDocument recipe, String slotId) {
        if (Texts.isBlank(slotId)) {
            return null;
        }
        for (CookingInputIngredient input : recipeService.fermentationInputIngredients(recipe)) {
            if (slotId.equalsIgnoreCase(input.slotId())) {
                return input;
            }
        }
        return null;
    }

    private boolean invalidFermentationIdentities(RecipeDocument recipe) {
        List<FermentationIdentityResolver.Identity> identities = new ArrayList<>();
        for (Map<String, Object> input : recipeService.fermentationInputs(recipe)) {
            String slotId = inputSlotId(input);
            String countKey = inputCountKey(input);
            if (Texts.isBlank(slotId) || Texts.isBlank(countKey)) {
                return true;
            }
            identities.add(new FermentationIdentityResolver.Identity(slotId, countKey));
        }
        return identities.isEmpty() || FermentationIdentityResolver.hasDuplicateSlotIds(identities);
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
        return String.format(Locale.ROOT, "%.2f%%", Math.min(100D, (double) done * 100D / (double) total));
    }

    private void tick() {
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
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.FERMENTATION_BARREL, coordinates)) {
            return;
        }
        FermentationBarrelState state = loadStateOrEmpty(coordinates);
        if (state == null || !state.valid() || !state.slotIdsResolved()) {
            activeStations.remove(coordinates);
            return;
        }
        Block block = coordinates.block();
        ItemSourceRef stationSource = stateStore.rememberedStationSource(coordinates);
        if (block == null || !blockMatcher.matches(block, StationType.FERMENTATION_BARREL, stationSource)) {
            removeState(coordinates, true);
            activeStations.remove(coordinates);
            return;
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

    private void ensureTicker() {
        if (activeStations.isEmpty() || (tickerTask != null && !tickerTask.cancelled())) {
            return;
        }
        tickerTask = taskScheduler.runGlobalTimer(plugin, this::tick, 20L, 20L);
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

    private boolean submitCompletion(Player player,
            Block block,
            StationCoordinates coordinates,
            FermentationBarrelState state,
            FermentationStage stage,
            boolean dropResult,
            boolean notifyPlayer) {
        if (state == null || !state.valid() || !state.slotIdsResolved()) {
            return false;
        }
        RecipeDocument recipe = recipeService.fermentationBarrelRecipeById(state.activeRecipeId());
        if (recipe == null || completionCoordinator == null) {
            return false;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        Map<String, Object> outcome = recipeService.fermentationOutcomeForStage(recipe, stage);
        List<CookingInputIngredient> inputs = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : state.slotSources().entrySet()) {
            int slot = entry.getKey();
            CookingInputIngredient configured = fermentationInputBySlotId(recipe, state.slotIds().get(slot));
            inputs.add(new CookingInputIngredient(
                    entry.getValue(),
                    state.slotAmounts().getOrDefault(slot, 1),
                    state.slotIds().getOrDefault(slot, ""),
                    state.slotCountKeys().getOrDefault(slot, ""),
                    configured == null ? List.of(entry.getValue()) : configured.itemSources(),
                    configured == null ? Map.of() : configured.matcher()));
        }
        String stageName = stage.name().toLowerCase(Locale.ROOT);
        boolean accepted = completionCoordinator.submit(new CookingCompletionRequest(
                "ferment:" + state.startedAtMs() + ":" + stageName,
                StationType.FERMENTATION_BARREL,
                coordinates,
                codec.serializeState(coordinates, state),
                CookingCompletionOperation.CommitMode.DELETE,
                Map.of(),
                recipe,
                player,
                location,
                dropResult,
                inputs,
                recipeService.outputs(outcome),
                recipeService.actions(outcome),
                "cooking_fermentation_barrel_complete",
                Map.of(
                        "recipe_id", recipe.id(),
                        "station_type", StationType.FERMENTATION_BARREL.folderName(),
                        "stage", stageName
                ),
                List.of(),

                null
        ));
        if (accepted && notifyPlayer && player != null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, collectionMessage(stage), Map.of());
            plugin.effectService().playActions(StationType.FERMENTATION_BARREL, "collect", player);
        }
        return accepted;
    }

    private void dropOriginalItems(Block block, FermentationBarrelState state) {
        if (block.getWorld() == null) {
            return;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            ItemStack item = codec.deserializeItem(state.slotItemData(entry.getKey()));
            if (item == null || item.getType().isAir()) {
                ItemSourceRef source = ItemSourceUtil.parse(entry.getValue());
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
        if (coordinates == null || state == null || !state.valid() || !state.slotIdsResolved()) {
            return;
        }
        if (state.isCompletelyEmpty()) {
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
        boolean needsCanonicalWriteback = loaded.needsSchemaWriteback() || !loaded.slotIdsResolved();
        boolean resolved = migrateSlotIds(loaded);
        if (!loaded.valid() || !resolved) {
            return loaded;
        }
        FermentationBarrelState existing = runtimeStates.putIfAbsent(coordinates, loaded);
        FermentationBarrelState result = existing == null ? loaded : existing;
        if (existing == null && needsCanonicalWriteback && !loaded.isCompletelyEmpty()) {
            loaded.markSchemaCurrent();
            stateStore.saveAsync(coordinates, codec.serializeState(coordinates, loaded));
        }
        return result;
    }

    Optional<StationCoordinates> viewingStation(UUID viewerId) {
        return Optional.ofNullable(guiController.viewingCoordinates(viewerId));
    }

    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        FermentationBarrelState state = loadStateOrEmpty(coordinates);
        if (state == null || state.isCompletelyEmpty()) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        long now = System.currentTimeMillis();
        RecipeDocument recipe = recipeService.fermentationBarrelRecipeById(state.activeRecipeId());

        int target = 0;
        int current = 0;
        double percent = 0.0D;
        long remaining = 0L;
        if (state.completed()) {
            percent = 100.0D;
            long total = Math.max(0L, state.finishAtMs() - state.startedAtMs());
            target = (int) (total / 1000L);
            current = target;
        } else if (state.fermenting()) {
            long total = Math.max(1L, state.finishAtMs() - state.startedAtMs());
            long done = Math.max(0L, Math.min(total, now - state.startedAtMs()));
            percent = Math.min(100.0D, (double) done * 100.0D / (double) total);
            target = (int) (total / 1000L);
            current = (int) (done / 1000L);
            remaining = Math.max(0L, (state.finishAtMs() - now) / 1000L);
        }

        String firstSource = "";
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            firstSource = entry.getValue();
            break;
        }
        return Optional.of(new StationSnapshot(
                StationType.FERMENTATION_BARREL,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(plugin, block),
                "",
                false,
                remaining,
                0, 0, 0,
                MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(firstSource).orElse("")),
                Texts.toStringSafe(firstSource),
                firstSource.isBlank() ? 0 : 1,
                state.slotSources().size(),
                recipe == null ? Texts.toStringSafe(state.activeRecipeId()) : recipe.id(),
                recipe == null ? "" : recipe.displayName(),
                current,
                target,
                percent,
                state.completed(),
                "",
                0,
                state.playerName() == null ? "" : state.playerName()
        ));
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
            FermentationBarrelState state = entry.getValue();
            if (state.valid() && state.slotIdsResolved() && !state.isCompletelyEmpty()) {
                stateStore.saveAsync(entry.getKey(), codec.serializeState(entry.getKey(), state));
            }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { guiController.onInventoryClose(event); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { guiController.onInventoryClick(event); }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { guiController.onInventoryDrag(event); }
}
