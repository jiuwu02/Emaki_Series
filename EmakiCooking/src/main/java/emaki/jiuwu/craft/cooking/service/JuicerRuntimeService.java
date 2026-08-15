package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

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
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
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
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class JuicerRuntimeService implements Listener {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final JuicerStateCodec codec = new JuicerStateCodec();
    private final JuicerGuiController guiController;
    private final CookingTextDisplayService textDisplayService;
    private final Map<StationCoordinates, JuicerState> runtimeStates = new ConcurrentHashMap<>();
    private CookingCompletionCoordinator completionCoordinator;

    public JuicerRuntimeService(EmakiCookingPlugin plugin,
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
        this.guiController = new JuicerGuiController(plugin, messageService, settingsService, itemSourceService, recipeService, codec);
        this.guiController.setRuntimeService(this);
    }

    public void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        this.completionCoordinator = completionCoordinator;
        if (completionCoordinator != null) {
            completionCoordinator.register(completionStateAccess());
        }
    }

    CookingStationStateAccess completionStateAccess() {
        return new CookingStationStateAccess() {
            @Override
            public StationType stationType() {
                return StationType.JUICER;
            }

            @Override
            public Map<String, Object> snapshot(StationCoordinates coordinates) {
                JuicerState state = runtimeStates.get(coordinates);
                if (state == null) {
                    state = codec.readState(stateStore.load(coordinates));
                }
                return state == null || state.isCompletelyEmpty() ? null : codec.serializeState(coordinates, state);
            }

            @Override
            public CompletionStage<Void> replace(
                    StationCoordinates coordinates,
                    Map<String, Object> committedState) {
                JuicerState state = codec.readState(new MapYamlSection(committedState));
                if (state == null || state.isCompletelyEmpty()) {
                    return CompletableFuture.failedFuture(
                            new IllegalArgumentException("Invalid committed juicer state"));
                }
                runtimeStates.put(coordinates, state);
                return stateStore.saveAsync(coordinates, committedState)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> refreshText(coordinates, state)));
            }

            @Override
            public CompletionStage<Void> delete(StationCoordinates coordinates) {
                runtimeStates.remove(coordinates);
                return stateStore.deleteAsync(coordinates)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            guiController.closeOpenInventories(coordinates, true);
                            textDisplayService.removeStation(StationType.JUICER, coordinates);
                        }));
            }
        };
    }

    public void reload() {
        guiController.closeAllOpenInventories(false);
        textDisplayService.removeStationType(StationType.JUICER);
        runtimeStates.clear();
        stateStore.forEachLoadedState(StationType.JUICER, this::restoreStoredState);
    }

    public boolean restoreStoredState(StationCoordinates coordinates, YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        Block block = coordinates.block();
        JuicerState state = codec.readState(section);
        ItemSourceRef stationSource = stateStore.stationSource(section);
        if (state == null || state.isCompletelyEmpty()) {
            removeState(coordinates, false);
            return false;
        }
        if (!blockMatcher.matches(block, StationType.JUICER, stationSource)) {
            removeState(coordinates, false);
            plugin.getLogger().warning("Station restore report: skipped_mismatch type=juicer coordinate=" + coordinates.runtimeKey());
            return false;
        }
        runtimeStates.put(coordinates, state);
        refreshText(coordinates, state);
        return true;
    }

    public void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        JuicerState state = runtimeStates.get(coordinates);
        if (state != null && !state.isCompletelyEmpty()) {
            stateStore.save(coordinates, codec.serializeState(coordinates, state));
        }
        removeState(coordinates, false);
    }

    public void shutdown() {
        guiController.closeAllOpenInventories(false);
        flushAll();
        textDisplayService.removeStationType(StationType.JUICER);
        runtimeStates.clear();
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand() || !blockMatcher.matches(interaction, StationType.JUICER)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.JUICER, coordinates)) {
            interaction.cancel();
            return true;
        }
        if (settingsService.matchesInteraction(StationType.JUICER, CookingSettingsService.INTERACTION_OPEN, interaction)) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.JUICER_USE) && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return guiController.openGui(player, coordinates);
        }
        if (settingsService.matchesInteraction(StationType.JUICER, CookingSettingsService.INTERACTION_SERVE, interaction)
                && servingTakesPrecedence(player, coordinates)) {
            interaction.cancel();
            return serve(player, block, coordinates);
        }
        if (settingsService.matchesInteraction(StationType.JUICER, CookingSettingsService.INTERACTION_PROCESS, interaction)) {
            JuicerState currentState = loadStateOrEmpty(coordinates);
            if (currentState.isCompletelyEmpty() && interaction.leftClick()) {
                return false;
            }
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.JUICER_PRESS) && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return press(player, block, coordinates);
        }
        if (settingsService.matchesInteraction(StationType.JUICER, CookingSettingsService.INTERACTION_INSPECT, interaction)) {
            interaction.cancel();
            return showInfo(player, coordinates);
        }
        return false;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(context, StationType.JUICER)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, context.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.JUICER, coordinates)) {
            return true;
        }
        JuicerGuiHolder openHolder = guiController.findOpenSession(coordinates);
        JuicerState state = openHolder == null ? loadStateOrEmpty(coordinates) : guiController.snapshotInventoryState(
                coordinates,
                openHolder.getInventory(),
                openHolder.viewerId(),
                Bukkit.getPlayer(openHolder.viewerId()) == null ? "" : Bukkit.getPlayer(openHolder.viewerId()).getName()
        );
        if (state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.JUICER, coordinates);
            return false;
        }
        guiController.closeOpenInventories(coordinates, true);
        dropOriginalItems(block, state);
        removeState(coordinates, true);
        return true;
    }

    private boolean press(Player player, Block block, StationCoordinates coordinates) {
        JuicerState state = loadStateOrEmpty(coordinates);
        if (state.isCompletelyEmpty()) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.no_item", Map.of());
            return true;
        }
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            RecipeDocument recipe = recipeService.findJuicerRecipe(entry.getValue(), player);
            if (recipe == null) {
                continue;
            }
            int slot = entry.getKey();
            int required = Math.max(1, recipeService.juicerPressesRequired(recipe));
            int next = state.progressAt(slot) + 1;
            if (next < required) {
                state.setProgress(slot, next);
                state.setPlayerContext(player.getUniqueId(), player.getName());
                saveState(coordinates, state);
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.pressed", Map.of("current", next, "required", required));
                plugin.effectService().playActions(StationType.JUICER, "press", player);
                return true;
            }
            if (recipeService.juicerHasFluidMode(recipe)) {
                state.setProgress(slot, required);
                return completeFluidPress(player, coordinates, state, slot, recipe);
            }
            CookingCompletionRequest.PlayerInventoryInput containerInput = requiredContainerInput(player, recipe);
            if (settingsService.juicerRequireContainer() && containerInput == null) {
                state.setProgress(slot, required);
                saveState(coordinates, state);
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.need_container", Map.of());
                return true;
            }
            Location pressLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
            Map<String, Object> pressPlaceholders = Map.of(
                    "recipe_id", recipe.id(),
                    "station_type", StationType.JUICER.folderName(),
                    "slot_index", slot
            );

            CookingRewardService.ConditionGate pressGate = rewardService.evaluateConditionGate(recipe, player);
            if (pressGate.blocked()) {
                state.setProgress(slot, required);
                saveState(coordinates, state);
                rewardService.runConditionFailActions(
                        pressGate.failActions(),
                        player,
                        pressLocation,
                        "cooking_juicer_complete",
                        pressPlaceholders
                );
                return true;
            }
            JuicerState committed = copyState(coordinates, state);
            committed.removeSlot(slot);
            committed.setPlayerContext(player.getUniqueId(), player.getName());
            Map<String, Object> outcome = recipeService.outcome(recipe, "result.success");
            boolean accepted = completionCoordinator != null && completionCoordinator.submit(new CookingCompletionRequest(
                    "press:" + slot + ":" + state.progressAt(slot) + ":" + System.currentTimeMillis(),
                    StationType.JUICER,
                    coordinates,
                    codec.serializeState(coordinates, state),
                    committed.isCompletelyEmpty() ? CookingCompletionOperation.CommitMode.DELETE : CookingCompletionOperation.CommitMode.SAVE,
                    committed.isCompletelyEmpty() ? Map.of() : codec.serializeState(coordinates, committed),
                    recipe,
                    player,
                    pressLocation,
                    settingsService.juicerDropResult(),
                    List.of(new CookingInputIngredient(state.slotSources().get(slot), 1)),
                    recipeService.outputs(outcome),
                    recipeService.actions(outcome),
                    "cooking_juicer_complete",
                    pressPlaceholders,
                    containerInput == null ? List.of() : List.of(containerInput),
                    pressGate.outcome()
            ));
            if (accepted) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.completed", Map.of("recipe", recipe.displayName()));
            }
            return true;
        }
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.no_recipe", Map.of());
        return true;
    }

    private boolean completeFluidPress(Player player, StationCoordinates coordinates, JuicerState state, int slot, RecipeDocument recipe) {
        String fluidId = recipeService.juicerFluidId(recipe);
        String fluidName = recipeService.juicerFluidDisplayName(recipe);
        int amountMl = recipeService.juicerFluidAmountMl(recipe);
        int maxMl = settingsService.juicerMaxFluidMl();
        if (state.hasFluid() && !state.fluidId().equalsIgnoreCase(fluidId)) {
            saveState(coordinates, state);
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.fluid_mismatch", Map.of(
                    "current_fluid", state.fluidDisplayName(),
                    "new_fluid", fluidName
            ));
            return true;
        }
        if (!state.canAcceptFluid(fluidId, amountMl, maxMl)) {
            saveState(coordinates, state);
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.fluid_full", Map.of(
                    "current", state.fluidAmountMl(),
                    "max", maxMl
            ));
            return true;
        }
        state.removeSlot(slot);
        state.addFluid(fluidId, fluidName, amountMl, maxMl);
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.fluid_added", Map.of(
                "fluid", state.fluidDisplayName(),
                "amount", amountMl,
                "current", state.fluidAmountMl(),
                "max", maxMl
        ));
        return true;
    }

    private CookingCompletionRequest.PlayerInventoryInput requiredContainerInput(Player player, RecipeDocument recipe) {
        if (!settingsService.juicerRequireContainer()) {
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemSourceRef identified = hand == null || hand.getType().isAir() ? null : itemSourceService.identifyItem(hand);
        if (identified == null) {
            return null;
        }
        for (ItemSourceRef source : recipeService.juicerContainerSources(recipe)) {
            if (ItemSourceUtil.matches(source, identified)) {
                return CookingCompletionRequest.PlayerInventoryInput.mainHand(player, 1, "juicer serving container");
            }
        }
        for (ItemSourceRef source : settingsService.juicerContainerSources()) {
            if (ItemSourceUtil.matches(source, identified)) {
                return CookingCompletionRequest.PlayerInventoryInput.mainHand(player, 1, "juicer serving container");
            }
        }
        return null;
    }

    private boolean servingTakesPrecedence(Player player, StationCoordinates coordinates) {
        JuicerState state = loadStateOrEmpty(coordinates);
        if (!state.hasFluid()) {
            return false;
        }
        if (!settingsService.juicerRequireContainer() || holdsServingContainer(player, state)) {
            return true;
        }
        return !hasPressableIngredient(player, state);
    }

    private boolean holdsServingContainer(Player player, JuicerState state) {
        RecipeDocument recipe = recipeService.findJuicerRecipeByFluidId(state.fluidId(), player);
        return recipe != null && requiredContainerInput(player, recipe) != null;
    }

    private boolean hasPressableIngredient(Player player, JuicerState state) {
        for (String source : state.slotSources().values()) {
            if (recipeService.findJuicerRecipe(source, player) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean serve(Player player, Block block, StationCoordinates coordinates) {
        JuicerState state = loadStateOrEmpty(coordinates);
        if (!state.hasFluid()) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.fluid_empty", Map.of());
            return true;
        }
        RecipeDocument recipe = recipeService.findJuicerRecipeByFluidId(state.fluidId(), player);
        if (recipe == null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.no_recipe", Map.of());
            return true;
        }
        int servingMl = recipeService.juicerServingMl(recipe);
        if (state.fluidAmountMl() < servingMl) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.fluid_not_enough", Map.of(
                    "fluid", state.fluidDisplayName(),
                    "required", servingMl,
                    "current", state.fluidAmountMl()
            ));
            return true;
        }
        CookingCompletionRequest.PlayerInventoryInput containerInput = requiredContainerInput(player, recipe);
        if (settingsService.juicerRequireContainer() && containerInput == null) {
            saveState(coordinates, state);
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.need_container", Map.of());
            return true;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        Map<String, Object> outcome = recipeService.outcome(recipe, "result.success");
        Map<String, Object> completionPlaceholders = Map.of(
                "recipe_id", recipe.id(),
                "station_type", StationType.JUICER.folderName(),
                "fluid_id", state.fluidId()
        );

        CookingRewardService.ConditionGate gate = rewardService.evaluateConditionGate(recipe, player);
        if (gate.blocked()) {
            rewardService.runConditionFailActions(
                    gate.failActions(),
                    player,
                    location,
                    "cooking_juicer_serve",
                    completionPlaceholders
            );
            return true;
        }
        JuicerState committed = copyState(coordinates, state);
        committed.consumeFluid(servingMl);
        committed.setPlayerContext(player.getUniqueId(), player.getName());
        boolean accepted = completionCoordinator != null && completionCoordinator.submit(new CookingCompletionRequest(
                "serve:" + state.fluidId() + ":" + state.fluidAmountMl() + ":" + System.currentTimeMillis(),
                StationType.JUICER,
                coordinates,
                codec.serializeState(coordinates, state),
                committed.isCompletelyEmpty() ? CookingCompletionOperation.CommitMode.DELETE : CookingCompletionOperation.CommitMode.SAVE,
                committed.isCompletelyEmpty() ? Map.of() : codec.serializeState(coordinates, committed),
                recipe,
                player,
                location,
                settingsService.juicerDropResult(),
                List.of(),
                recipeService.outputs(outcome),
                recipeService.actions(outcome),
                "cooking_juicer_serve",
                completionPlaceholders,
                containerInput == null ? List.of() : List.of(containerInput),
                gate.outcome()
        ));
        if (!accepted) {
            return true;
        }
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.served", Map.of(
                "fluid", recipeService.juicerFluidDisplayName(recipe),
                "amount", servingMl,
                "current", committed.fluidAmountMl(),
                "max", settingsService.juicerMaxFluidMl()
        ));
        plugin.effectService().playActions(StationType.JUICER, "serve", player);
        return true;
    }

    private boolean showInfo(Player player, StationCoordinates coordinates) {
        JuicerState state = loadStateOrEmpty(coordinates);
        int total = 0;
        int progress = 0;
        for (Map.Entry<Integer, String> entry : state.slotSources().entrySet()) {
            RecipeDocument recipe = recipeService.findJuicerRecipe(entry.getValue(), player);
            if (recipe == null) {
                continue;
            }
            total += Math.max(1, recipeService.juicerPressesRequired(recipe));
            progress += Math.min(recipeService.juicerPressesRequired(recipe), state.progressAt(entry.getKey()));
        }
        String text = total <= 0 ? messageService.message("juicer.progress_not_started") : progress + "/" + total;
        String fluidText = state.hasFluid()
                ? state.fluidDisplayName() + " " + state.fluidAmountMl() + "/" + settingsService.juicerMaxFluidMl() + "ml"
                : messageService.message("juicer.fluid_none");
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "juicer.info", Map.of("progress", text, "fluid", fluidText));
        return true;
    }

    private void dropOriginalItems(Block block, JuicerState state) {
        if (block.getWorld() == null) {
            return;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            ItemStack item = codec.deserializeItem(state.slotItemData(entry.getKey()));
            if (item == null || item.getType().isAir()) {
                ItemSourceRef source = ItemSourceUtil.parse(entry.getValue());
                item = source == null ? null : itemSourceService.createItem(source, 1);
            }
            if (item != null && !item.getType().isAir()) {
                block.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    JuicerState saveInventory(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        JuicerState state = guiController.snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
        saveState(coordinates, state);
        return state;
    }

    private JuicerState copyState(StationCoordinates coordinates, JuicerState state) {
        return codec.readState(new MapYamlSection(codec.serializeState(coordinates, state)));
    }

    void saveState(StationCoordinates coordinates, JuicerState state) {
        if (coordinates == null || state == null || state.isCompletelyEmpty()) {
            removeState(coordinates, true);
            return;
        }
        runtimeStates.put(coordinates, state);
        stateStore.saveAsync(coordinates, codec.serializeState(coordinates, state));
        refreshText(coordinates, state);
    }

    JuicerState loadStateOrEmpty(StationCoordinates coordinates) {
        if (coordinates == null) {
            return new JuicerState();
        }
        JuicerState cached = runtimeStates.get(coordinates);
        if (cached != null) {
            return cached;
        }
        JuicerState loaded = codec.readState(stateStore.load(coordinates));
        runtimeStates.putIfAbsent(coordinates, loaded);
        return loaded;
    }

    Optional<StationCoordinates> viewingStation(UUID viewerId) {
        return Optional.ofNullable(guiController.viewingCoordinates(viewerId));
    }

    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        JuicerState state = loadStateOrEmpty(coordinates);
        if (state == null || state.isCompletelyEmpty()) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        int totalRequired = 0;
        int totalProgress = 0;
        String firstSource = "";
        RecipeDocument firstRecipe = null;
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            if (Texts.isBlank(firstSource)) {
                firstSource = entry.getValue();
            }
            RecipeDocument recipe = recipeService.findJuicerRecipe(entry.getValue(), null);
            if (recipe == null) {
                continue;
            }
            if (firstRecipe == null) {
                firstRecipe = recipe;
            }
            int required = Math.max(1, recipeService.juicerPressesRequired(recipe));
            totalRequired += required;
            totalProgress += Math.min(required, state.progressAt(entry.getKey()));
        }
        double percent = totalRequired > 0 ? Math.min(100.0D, (double) totalProgress * 100.0D / (double) totalRequired) : 0.0D;
        return Optional.of(new StationSnapshot(
                StationType.JUICER,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(plugin, block),
                "",
                false,
                0L,
                0, 0, 0,
                MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(firstSource).orElse("")),
                Texts.toStringSafe(firstSource),
                firstSource.isBlank() ? 0 : 1,
                state.slotSources().size(),
                firstRecipe == null ? "" : firstRecipe.id(),
                firstRecipe == null ? "" : firstRecipe.displayName(),
                totalProgress,
                totalRequired,
                percent,
                false,
                state.hasFluid() ? MiniMessages.plainText(state.fluidDisplayName()) : "",
                state.fluidAmountMl(),
                state.playerName() == null ? "" : state.playerName()
        ));
    }

    void removeState(StationCoordinates coordinates, boolean deleteFile) {
        if (coordinates == null) {
            return;
        }
        runtimeStates.remove(coordinates);
        textDisplayService.removeStation(StationType.JUICER, coordinates);
        if (deleteFile) {
            stateStore.deleteAsync(coordinates);
        }
    }

    private void refreshText(StationCoordinates coordinates, JuicerState state) {
        if (!settingsService.textDisplayEnabled(StationType.JUICER) || coordinates == null
                || state == null || state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.JUICER, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.JUICER, coordinates);
            return;
        }
        int total = 0;
        int progress = 0;
        for (Map.Entry<Integer, String> entry : state.slotSources().entrySet()) {
            RecipeDocument recipe = recipeService.findJuicerRecipe(entry.getValue(), null);
            if (recipe == null) {
                continue;
            }
            int required = Math.max(1, recipeService.juicerPressesRequired(recipe));
            total += required;
            progress += Math.min(required, state.progressAt(entry.getKey()));
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.juicer.title"));
        boolean hasSlots = !state.slotSources().isEmpty();
        if (hasSlots) {
            String progressText = total <= 0 ? messageService.message("juicer.progress_not_started") : progress + "/" + total;
            appendLine(builder, messageService.message("text_display.juicer.progress", Map.of("progress", progressText)));
            appendLine(builder, messageService.message("text_display.juicer.hint_press"));
        }
        if (state.hasFluid()) {
            appendLine(builder, messageService.message("text_display.juicer.fluid", Map.of(
                    "fluid", state.fluidDisplayName(),
                    "amount", state.fluidAmountMl(),
                    "max", settingsService.juicerMaxFluidMl()
            )));
            appendLine(builder, messageService.message("text_display.juicer.hint_serve"));
        }
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.JUICER,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.JUICER)
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
        for (Map.Entry<StationCoordinates, JuicerState> entry : runtimeStates.entrySet()) {
            if (!entry.getValue().isCompletelyEmpty()) {
                stateStore.saveAsync(entry.getKey(), codec.serializeState(entry.getKey(), entry.getValue()));
            }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) { guiController.onInventoryClose(event); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { guiController.onInventoryClick(event); }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { guiController.onInventoryDrag(event); }
}
