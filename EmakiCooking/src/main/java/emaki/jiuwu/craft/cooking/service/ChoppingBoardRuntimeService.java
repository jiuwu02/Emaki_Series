package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;

import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplaySpec;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class ChoppingBoardRuntimeService {

    private static final double DISPLAY_SEARCH_RADIUS = 1.5D;

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final ItemSourceService itemSourceService;
    private final CookingDisplayService displayService;
    private final CookingTextDisplayService textDisplayService;
    private CookingCompletionCoordinator completionCoordinator;

    public ChoppingBoardRuntimeService(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingRecipeService recipeService,
            ItemSourceService itemSourceService,
            CookingDisplayService displayService,
            CookingTextDisplayService textDisplayService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.recipeService = recipeService;
        this.itemSourceService = itemSourceService;
        this.displayService = Objects.requireNonNull(displayService, "displayService");
        this.textDisplayService = Objects.requireNonNull(textDisplayService, "textDisplayService");
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
                return StationType.CHOPPING_BOARD;
            }

            @Override
            public Map<String, Object> snapshot(StationCoordinates coordinates) {
                ChoppingBoardState state = readState(stateStore.load(coordinates));
                return state == null ? null : serializeState(coordinates, state);
            }

            @Override
            public CompletionStage<Void> replace(
                    StationCoordinates coordinates,
                    Map<String, Object> committedState) {
                ChoppingBoardState state = readState(new MapYamlSection(committedState));
                if (state == null) {
                    return CompletableFuture.failedFuture(
                            new IllegalArgumentException("Invalid committed chopping-board state"));
                }
                return stateStore.saveAsync(coordinates, committedState)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            refreshDisplay(coordinates, state.inputSource(), state.inputItemData());
                            refreshText(coordinates, state);
                        }));
            }

            @Override
            public CompletionStage<Void> delete(StationCoordinates coordinates) {
                ChoppingBoardState state = readState(stateStore.load(coordinates));
                return stateStore.deleteAsync(coordinates)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            clearDisplay(coordinates, state == null ? null : state.displayEntityId(), state == null ? null : state.inputSource());
                            textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
                        }));
            }
        };
    }

    public void reload() {
        displayService.removeStationType(StationType.CHOPPING_BOARD);
        textDisplayService.removeStationType(StationType.CHOPPING_BOARD);
        stateStore.forEachLoadedState(StationType.CHOPPING_BOARD, this::restoreStoredState);
    }

    public boolean restoreStoredState(StationCoordinates coordinates, YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        ChoppingBoardState state = readState(section);
        ItemSourceRef stationSource = stateStore.stationSource(section);
        Block block = coordinates.block();
        if (state == null) {
            clearDisplay(coordinates, null, null);
            textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
            return false;
        }
        if (!blockMatcher.matches(block, StationType.CHOPPING_BOARD, stationSource)) {
            clearDisplay(coordinates, state.displayEntityId(), state.inputSource());
            textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
            plugin.getLogger().warning("Station restore report: skipped_mismatch type=chopping_board coordinate=" + coordinates.runtimeKey());
            return false;
        }
        if (state.hasInputSource()) {
            if (state.displayEntityId() != null) {
                clearDisplay(coordinates, state.displayEntityId(), state.inputSource());
            }
            refreshDisplay(coordinates, state.inputSource(), state.inputItemData());
            refreshText(coordinates, state);
            if (state.displayEntityId() != null) {
                saveState(coordinates, new ChoppingBoardState(
                        state.inputSource(),
                        state.inputItemData(),
                        state.inputAmount(),
                        state.cutCount(),
                        state.lastInteractionMs(),
                        null
                ));
            }
        }
        return true;
    }

    public void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        displayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
        textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        if (block == null || !interaction.mainHand() || !blockMatcher.matches(interaction, StationType.CHOPPING_BOARD)) {
            return false;
        }
        Player player = interaction.player();
        if (player == null) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.CHOPPING_BOARD, coordinates)) {
            interaction.cancel();
            return true;
        }
        ChoppingBoardState state = readState(stateStore.load(coordinates));
        long now = System.currentTimeMillis();
        debugStation("station.chopping_interact", Map.of(
                "player", player.getName(),
                "station", coordinates.runtimeKey(),
                "interaction", interaction.type() == null ? "none" : interaction.type().configKey(),
                "state", state == null
                        ? "null"
                        : "input=" + state.inputSource() + ",amount=" + state.inputAmount() + ",cuts=" + state.cutCount()
        ));

        if (settingsService.matchesInteraction(
                StationType.CHOPPING_BOARD,
                CookingSettingsService.INTERACTION_RETURN_INPUT,
                interaction)) {
            return handleReturnInput(interaction, player, coordinates, state);
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (state != null && state.hasInputSource()) {
            return handleOccupiedBoard(interaction, block, player, coordinates, state, hand, now);
        }

        return handlePlaceInput(interaction, block, player, coordinates, hand, now);
    }

    private boolean handleReturnInput(StationInteraction interaction,
            Player player,
            StationCoordinates coordinates,
            ChoppingBoardState state) {
        if (state == null || !state.hasInputSource()) {
            debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "return_input",
                    "reason", state == null ? "no_state" : "no_input_source"
            ));
            return false;
        }
        returnStoredInput(player, coordinates, state);
        interaction.cancel();
        return true;
    }

    private boolean handleOccupiedBoard(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            ChoppingBoardState state,
            ItemStack hand,
            long now) {
        if (settingsService.matchesInteraction(
                StationType.CHOPPING_BOARD,
                CookingSettingsService.INTERACTION_PLACE_INPUT,
                interaction)
                && appendInput(player, block, coordinates, state, hand, now)) {
            interaction.cancel();
            return true;
        }
        if (!settingsService.matchesInteraction(
                StationType.CHOPPING_BOARD,
                CookingSettingsService.INTERACTION_PROCESS,
                interaction)) {
            return false;
        }
        if (settingsService.choppingSpaceRestriction() && block.getRelative(BlockFace.UP).getType() != Material.AIR) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.CHOPPING_BOARD_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        if (settingsService.choppingInteractionDelayMs() > 0L
                && now - state.lastInteractionMs() < settingsService.choppingInteractionDelayMs()) {
            debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "process",
                    "reason", "too_fast elapsed=" + (now - state.lastInteractionMs())
                            + "ms required=" + settingsService.choppingInteractionDelayMs() + "ms"
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.too_fast", Map.of());
            interaction.cancel();
            return true;
        }
        if (hand == null || hand.getType().isAir()) {
            debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "process",
                    "reason", "empty_hand"
            ));
            return false;
        }
        if (!isTool(hand)) {
            debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "process",
                    "reason", "wrong_tool hand=" + hand.getType().getKey()
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.wrong_tool", Map.of());
            interaction.cancel();
            return true;
        }
        if (!player.hasPermission(CookingPermissions.CHOPPING_BOARD_CUT)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        return handleCut(interaction, block, player, coordinates, state, hand, now);
    }

    private boolean handleCut(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            ChoppingBoardState state,
            ItemStack hand,
            long now) {
        RecipeDocument recipe = recipeService.findChoppingBoardRecipe(state.inputSource(), player);
        if (recipe == null) {
            debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "process",
                    "reason", "no_recipe_for_input input=" + state.inputSource()
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.cannot_cut", Map.of());
            interaction.cancel();
            return true;
        }
        int cutsRequired = recipeService.choppingCutsRequired(recipe);
        if (cutsRequired <= 0) {
            debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "process",
                    "reason", "cuts_required_not_positive recipe=" + recipe.id()
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.cannot_cut", Map.of());
            interaction.cancel();
            return true;
        }
        int inputRequired = recipeService.choppingInputAmount(recipe);
        if (state.inputAmount() < inputRequired) {
            debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "process",
                    "reason", "not_enough_input current=" + state.inputAmount() + " required=" + inputRequired
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.not_enough_input", Map.of(
                    "current", state.inputAmount(),
                    "required", inputRequired
            ));
            refreshText(coordinates, state);
            interaction.cancel();
            return true;
        }

        int nextCutCount = state.cutCount() + 1;
        debugStation("station.chopping_cut", Map.of(
                "player", player.getName(),
                "station", coordinates.runtimeKey(),
                "recipe", recipe.id(),
                "cut", nextCutCount,
                "required", cutsRequired
        ));
        applyToolDamage(player, hand, recipeService.choppingToolDamage(recipe));
        maybeDamagePlayer(player, recipeService.choppingDamageChance(recipe), recipeService.choppingDamageValue(recipe));
        plugin.effectService().playActions(StationType.CHOPPING_BOARD, "cut", player);

        if (nextCutCount >= cutsRequired) {
            return completeCut(interaction, block, player, coordinates, state, recipe, nextCutCount, inputRequired, now);
        }

        ChoppingBoardState updated = new ChoppingBoardState(
                state.inputSource(),
                state.inputItemData(),
                state.inputAmount(),
                nextCutCount,
                now,
                state.displayEntityId()
        );
        saveState(coordinates, updated);
        refreshText(coordinates, updated);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.progress", Map.of(
                "current", nextCutCount,
                "required", cutsRequired
        ));
        interaction.cancel();
        return true;
    }

    private boolean completeCut(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            ChoppingBoardState state,
            RecipeDocument recipe,
            int nextCutCount,
            int inputRequired,
            long now) {
        int remainingAmount = Math.max(0, state.inputAmount() - inputRequired);
        ChoppingBoardState remaining = remainingAmount <= 0 ? null : new ChoppingBoardState(
                state.inputSource(),
                state.inputItemData(),
                remainingAmount,
                0,
                now,
                state.displayEntityId()
        );
        boolean accepted = completionCoordinator != null && completionCoordinator.submit(new CookingCompletionRequest(
                "cut:" + state.lastInteractionMs() + ":" + nextCutCount,
                StationType.CHOPPING_BOARD,
                coordinates,
                serializeState(coordinates, state),
                remaining == null ? CookingCompletionOperation.CommitMode.DELETE : CookingCompletionOperation.CommitMode.SAVE,
                remaining == null ? Map.of() : serializeState(coordinates, remaining),
                recipe,
                player,
                block.getLocation().add(0.5D, 1.0D, 0.5D),
                settingsService.choppingDropResult(),
                List.of(new CookingInputIngredient(state.inputSource(), inputRequired)),
                recipeService.outputs(recipe),
                recipeService.actions(recipe),
                "cooking_chopping_board_complete",
                Map.of(
                        "recipe_id", recipe.id(),
                        "station_type", StationType.CHOPPING_BOARD.folderName()
                ),
                List.of(),

                null
        ));
        if (accepted) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.completed", Map.of("recipe", recipe.displayName()));
            plugin.effectService().playActions(StationType.CHOPPING_BOARD, "complete", player);
        }
        interaction.cancel();
        return true;
    }

    private boolean handlePlaceInput(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            ItemStack hand,
            long now) {
        if (!settingsService.matchesInteraction(
                StationType.CHOPPING_BOARD,
                CookingSettingsService.INTERACTION_PLACE_INPUT,
                interaction)) {
            return false;
        }
        if (settingsService.choppingSpaceRestriction() && block.getRelative(BlockFace.UP).getType() != Material.AIR) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.CHOPPING_BOARD_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        ItemSourceRef source = itemSourceService.identifyItem(hand);
        String shorthand = source == null ? null : ItemSourceUtil.toShorthand(source);
        if (shorthand == null || shorthand.isBlank()) {
            return false;
        }
        if (settingsService.onlyRecipeItems(StationType.CHOPPING_BOARD)
                && recipeService.findChoppingBoardRecipe(shorthand, player, hand) == null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "general.input_rejected", Map.of());
            interaction.cancel();
            return true;
        }
        ItemStack storedStack = takeMainHandStack(player);
        if (storedStack == null || storedStack.getType().isAir()) {
            return false;
        }
        int placedAmount = storedStack.getAmount();
        Map<String, Object> itemData = serializeItemTemplate(storedStack);
        refreshDisplay(coordinates, shorthand, itemData);
        ChoppingBoardState updated = new ChoppingBoardState(shorthand, itemData, placedAmount, 0, now, null);
        saveState(coordinates, updated);
        refreshText(coordinates, updated);
        RecipeDocument recipe = recipeService.findChoppingBoardRecipe(shorthand, player);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.item_placed", Map.of(
                "amount", placedAmount,
                "total", updated.inputAmount(),
                "required", recipe == null ? 1 : recipeService.choppingInputAmount(recipe)
        ));
        plugin.effectService().playActions(StationType.CHOPPING_BOARD, "place", player);
        interaction.cancel();
        return true;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(context, StationType.CHOPPING_BOARD)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, context.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.CHOPPING_BOARD, coordinates)) {
            return true;
        }
        ChoppingBoardState state = readState(stateStore.load(coordinates));
        if (state == null) {
            debugStation("station.break_without_state", Map.of(
                    "station", coordinates.runtimeKey(),
                    "type", StationType.CHOPPING_BOARD.folderName()
            ));
            clearDisplay(coordinates, null, null);
            textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
            return false;
        }
        if (state.hasInputSource()) {
            for (ItemStack itemStack : storedInputStacks(state.inputSource(), state.inputItemData(), state.inputAmount())) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 1.0D, 0.5D), itemStack);
            }
        }
        clearDisplay(coordinates, state.displayEntityId(), state.inputSource());
        textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
        stateStore.deleteAsync(coordinates);
        return true;
    }

    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        ChoppingBoardState state = readState(stateStore.load(coordinates));
        if (state == null || !state.hasInputSource()) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        RecipeDocument recipe = recipeService.findChoppingBoardRecipe(state.inputSource(), null);
        int target = recipe == null ? 0 : recipeService.choppingCutsRequired(recipe);
        int current = Math.min(state.cutCount(), target > 0 ? target : state.cutCount());
        double percent = target > 0 ? Math.min(100.0D, (double) current * 100.0D / (double) target) : 0.0D;
        return Optional.of(new StationSnapshot(
                StationType.CHOPPING_BOARD,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(plugin, block),
                "",
                false,
                0L,
                0, 0, 0,
                plainItemName(state.inputSource()),
                state.inputSource(),
                state.inputAmount(),
                1,
                recipe == null ? "" : recipe.id(),
                recipe == null ? "" : recipe.displayName(),
                current,
                target,
                percent,
                false,
                "",
                0,
                ""
        ));
    }

    private String plainItemName(String source) {
        return MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(source).orElse(""));
    }

    private boolean appendInput(Player player, Block block, StationCoordinates coordinates, ChoppingBoardState state, ItemStack hand, long now) {
        if (player == null || block == null || coordinates == null || state == null || !state.hasInputSource()
                || hand == null || hand.getType().isAir()) {
            return false;
        }
        ItemSourceRef source = itemSourceService.identifyItem(hand);
        String shorthand = source == null ? null : ItemSourceUtil.toShorthand(source);
        if (shorthand == null || shorthand.isBlank() || !matchesInputSource(shorthand, state.inputSource())) {
            return false;
        }
        if (recipeService.findChoppingBoardRecipe(shorthand, player) != null
                && recipeService.findChoppingBoardRecipe(shorthand, player, hand) == null) {
            return false;
        }
        if (settingsService.choppingSpaceRestriction() && block.getRelative(BlockFace.UP).getType() != Material.AIR) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.CHOPPING_BOARD_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            return true;
        }
        ItemStack storedStack = takeMainHandStack(player);
        if (storedStack == null || storedStack.getType().isAir()) {
            return false;
        }
        int addedAmount = storedStack.getAmount();
        ChoppingBoardState updated = new ChoppingBoardState(
                state.inputSource(),
                state.inputItemData().isEmpty() ? serializeItemTemplate(storedStack) : state.inputItemData(),
                addAmounts(state.inputAmount(), addedAmount),
                state.cutCount(),
                now,
                state.displayEntityId()
        );
        saveState(coordinates, updated);
        refreshDisplay(coordinates, updated.inputSource(), updated.inputItemData());
        refreshText(coordinates, updated);
        RecipeDocument recipe = recipeService.findChoppingBoardRecipe(updated.inputSource(), player);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.item_placed", Map.of(
                "amount", addedAmount,
                "total", updated.inputAmount(),
                "required", recipe == null ? 1 : recipeService.choppingInputAmount(recipe)
        ));
        plugin.effectService().playActions(StationType.CHOPPING_BOARD, "place", player);
        return true;
    }

    private boolean matchesInputSource(String candidateSource, String storedSource) {
        ItemSourceRef candidate = ItemSourceUtil.parse(candidateSource);
        ItemSourceRef stored = ItemSourceUtil.parse(storedSource);
        if (candidate != null && stored != null) {
            return ItemSourceUtil.matches(stored, candidate);
        }
        return candidateSource != null && storedSource != null && candidateSource.equalsIgnoreCase(storedSource);
    }

    private ItemStack takeMainHandStack(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return null;
        }
        ItemStack consumed = hand.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        return consumed;
    }

    private Map<String, Object> serializeItemTemplate(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }
        ItemStack template = itemStack.clone();
        template.setAmount(1);
        return StoredItemCodec.serialize(template);
    }

    private int addAmounts(int current, int added) {
        long total = (long) Math.max(0, current) + Math.max(0, added);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) total);
    }

    private boolean isTool(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemSourceRef source = itemSourceService.identifyItem(itemStack);
        if (source == null) {
            return false;
        }
        for (ItemSourceRef tool : settingsService.choppingToolSources()) {
            if (ItemSourceUtil.matches(tool, source)) {
                return CookingMatchers.test(settingsService.choppingToolMatcher(), itemStack, source, null);
            }
        }
        return false;
    }

    private void returnStoredInput(Player player, StationCoordinates coordinates, ChoppingBoardState state) {
        clearDisplay(coordinates, state == null ? null : state.displayEntityId(), state == null ? null : state.inputSource());
        textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
        stateStore.deleteAsync(coordinates);
        if (state == null || !state.hasInputSource()) {
            return;
        }
        List<ItemStack> itemStacks = storedInputStacks(state.inputSource(), state.inputItemData(), state.inputAmount());
        if (itemStacks.isEmpty()) {
            return;
        }
        giveStoredInput(player, itemStacks);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.item_returned", Map.of(
                "amount", state.inputAmount()
        ));
    }

    private void applyToolDamage(Player player, ItemStack itemStack, int amount) {
        if (player == null || itemStack == null || itemStack.getType().isAir() || amount <= 0) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int maxDurability = itemStack.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return;
        }
        int nextDamage = damageable.getDamage() + amount;
        if (nextDamage >= maxDurability) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        damageable.setDamage(nextDamage);
        itemStack.setItemMeta(meta);
        player.getInventory().setItemInMainHand(itemStack);
    }

    private void maybeDamagePlayer(Player player, Integer chance, Integer value) {
        if (player == null || chance == null || value == null || chance <= 0 || value <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= chance) {
            return;
        }
        player.damage(value);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.cut_hurt", Map.of("damage", value));
    }

    private void saveState(StationCoordinates coordinates, ChoppingBoardState state) {
        stateStore.saveAsync(coordinates, serializeState(coordinates, state));
    }

    private Map<String, Object> serializeState(StationCoordinates coordinates, ChoppingBoardState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.CHOPPING_BOARD, coordinates);
        if (state.hasInputSource()) {
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("source", state.inputSource());
            inputItem.put("amount", state.inputAmount());
            if (state.inputItemData() != null && !state.inputItemData().isEmpty()) {
                inputItem.put("item", state.inputItemData());
            }
            root.put("input_item", inputItem);
        }
        root.put("timestamps", Map.of("last_interaction_ms", state.lastInteractionMs()));
        root.put("chopping_board", Map.of("cut_count", state.cutCount()));
        return root;
    }

    private ChoppingBoardState readState(YamlSection section) {
        if (section == null || !StationType.CHOPPING_BOARD.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return null;
        }
        String inputSource = section.getString("input_item.source", "");
        Map<String, Object> inputItemData = readItemData(section.get("input_item.item"));
        int fallbackAmount = Math.max(1, CookingRuntimeUtil.parseInteger(inputItemData.get("amount"), 1));
        Integer inputAmount = section.getInt("input_item.amount", fallbackAmount);
        Integer cutCount = section.getInt("chopping_board.cut_count", 0);
        UUID displayId = CookingRuntimeUtil.parseUuid(section.getString("display_entity.uuid", ""));
        long lastInteraction = CookingRuntimeUtil.parseLong(section.get("timestamps.last_interaction_ms"), 0L);
        return new ChoppingBoardState(
                inputSource,
                inputItemData,
                inputAmount == null ? fallbackAmount : inputAmount,
                cutCount == null ? 0 : cutCount,
                lastInteraction,
                displayId
        );
    }

    private void refreshDisplay(StationCoordinates coordinates, String inputSource, Map<String, Object> inputItemData) {
        ItemSourceRef source = ItemSourceUtil.parse(inputSource);
        ItemStack itemStack = storedItemOrFallback(inputSource, inputItemData, 1);
        if (source == null && itemStack != null && !itemStack.getType().isAir()) {
            source = itemSourceService.identifyItem(itemStack);
        }
        if (source == null || itemStack == null || itemStack.getType().isAir()) {
            displayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
            return;
        }
        CookingSettingsService.DisplayAdjustmentProfile adjustment = settingsService.displayAdjustment(
                StationType.CHOPPING_BOARD,
                source,
                itemStack.getType().isBlock()
        );
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            displayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
            return;
        }
        displayService.upsert(new CookingDisplaySpec(
                StationType.CHOPPING_BOARD,
                coordinates,
                "input",
                itemStack,
                baseLocation,
                adjustment,
                null
        ));
    }

    private Map<String, Object> readItemData(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (!(plain instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(map));
    }

    private ItemStack storedItemOrFallback(String sourceText, Map<String, Object> itemData, int amount) {
        ItemStack storedItem = StoredItemCodec.deserialize(itemData);
        if (storedItem != null && !storedItem.getType().isAir()) {
            storedItem.setAmount(Math.max(1, amount));
            return storedItem;
        }
        ItemSourceRef source = ItemSourceUtil.parse(sourceText);
        return source == null ? null : itemSourceService.createItem(source, amount);
    }

    private List<ItemStack> storedInputStacks(String sourceText, Map<String, Object> itemData, int amount) {
        ItemStack template = storedItemOrFallback(sourceText, itemData, 1);
        if (template == null || template.getType().isAir()) {
            return List.of();
        }
        int remaining = Math.max(0, amount);
        if (remaining <= 0) {
            return List.of();
        }
        int maxStackSize = Math.max(1, template.getType().getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack stack = template.clone();
            stack.setAmount(stackAmount);
            stacks.add(stack);
            remaining -= stackAmount;
        }
        return List.copyOf(stacks);
    }

    private void giveStoredInput(Player player, List<ItemStack> itemStacks) {
        if (player == null || itemStacks == null || itemStacks.isEmpty()) {
            return;
        }
        boolean filledHand = false;
        ItemStack hand = player.getInventory().getItemInMainHand();
        for (ItemStack itemStack : itemStacks) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            if (!filledHand && (hand == null || hand.getType().isAir())) {
                player.getInventory().setItemInMainHand(itemStack);
                filledHand = true;
                continue;
            }
            InventoryItemUtil.giveOrDrop(player, itemStack);
        }
    }

    private String inputDisplayName(ChoppingBoardState state) {
        if (state == null || !state.hasInputSource()) {
            return "";
        }
        String storedName = storedItemDisplayName(state.inputItemData());
        if (Texts.isNotBlank(storedName)) {
            return storedName;
        }
        String sourceName = EmakiCoreLibApi.itemDisplayName(state.inputSource()).orElse("");
        return Texts.isBlank(sourceName) ? state.inputSource() : sourceName;
    }

    private String storedItemDisplayName(Map<String, Object> itemData) {
        ItemStack storedItem = StoredItemCodec.deserialize(itemData);
        if (storedItem == null || storedItem.getType().isAir()) {
            return "";
        }
        ItemMeta itemMeta = storedItem.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        String displayName = MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
        return Texts.isBlank(displayName) ? "" : displayName;
    }

    private void refreshText(StationCoordinates coordinates, ChoppingBoardState state) {
        if (!settingsService.textDisplayEnabled(StationType.CHOPPING_BOARD)
                || coordinates == null || state == null || !state.hasInputSource()) {
            textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
            return;
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.chopping_board.title"));
        String itemName = inputDisplayName(state);
        appendLine(builder, messageService.message("text_display.chopping_board.placed", Map.of("item", itemName)));
        RecipeDocument recipe = recipeService.findChoppingBoardRecipe(state.inputSource(), null);
        int cutsRequired = recipe == null ? 0 : recipeService.choppingCutsRequired(recipe);
        int inputRequired = recipe == null ? 1 : recipeService.choppingInputAmount(recipe);
        appendLine(builder, messageService.message("text_display.chopping_board.amount", Map.of(
                "current", state.inputAmount(),
                "required", inputRequired
        )));
        if (recipe == null || cutsRequired <= 0) {
            appendLine(builder, messageService.message("text_display.chopping_board.no_recipe"));
        } else {
            appendLine(builder, messageService.message("text_display.chopping_board.progress", Map.of(
                    "current", state.cutCount(),
                    "required", cutsRequired
            )));
            appendLine(builder, messageService.message(state.inputAmount() < inputRequired
                    ? "text_display.chopping_board.hint_add"
                    : "text_display.chopping_board.hint_cut"));
        }
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.CHOPPING_BOARD,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.CHOPPING_BOARD)
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

    private void debugStation(String langKey, Map<String, ?> replacements) {
        if (plugin == null || plugin.debugLogger() == null) {
            return;
        }
        plugin.debugLogger().log("station", (UUID) null, langKey, replacements);
    }

    private void clearDisplay(StationCoordinates coordinates, UUID knownId, String inputSource) {
        if (coordinates == null) {
            return;
        }
        displayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
        if (knownId == null) {
            return;
        }
        Location baseLocation = coordinates.location(0.5D, 0.5D, 0.5D);
        Location targetLocation = resolveDisplayLocation(coordinates, inputSource);
        Location searchLocation = targetLocation == null ? baseLocation : targetLocation;
        if (searchLocation != null && searchLocation.getWorld() != null) {
            Entity trackedEntity = Bukkit.getEntity(knownId);
            if (trackedEntity instanceof ItemDisplay) {
                trackedEntity.remove();
            }
        }
        if (searchLocation != null && searchLocation.getWorld() != null) {
            for (Entity entity : searchLocation.getWorld().getNearbyEntities(searchLocation,
                    DISPLAY_SEARCH_RADIUS,
                    DISPLAY_SEARCH_RADIUS,
                    DISPLAY_SEARCH_RADIUS)) {
                if (entity instanceof ItemDisplay) {
                    entity.remove();
                }
            }
        }
    }

    private Location resolveDisplayLocation(StationCoordinates coordinates, String inputSource) {
        if (coordinates == null || Texts.isBlank(inputSource)) {
            return null;
        }
        ItemSourceRef source = ItemSourceUtil.parse(inputSource);
        ItemStack itemStack = itemSourceService.createItem(source, 1);
        if (source == null || itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        CookingSettingsService.DisplayAdjustmentProfile adjustment = settingsService.displayAdjustment(
                StationType.CHOPPING_BOARD,
                source,
                itemStack.getType().isBlock()
        );
        return adjustment.applyOffset(coordinates.location(0D, 0D, 0D));
    }

    private record ChoppingBoardState(String inputSource,
            Map<String, Object> inputItemData,
            int inputAmount,
            int cutCount,
            long lastInteractionMs,
            UUID displayEntityId) {

        private ChoppingBoardState {
            inputItemData = inputItemData == null || inputItemData.isEmpty() ? Map.of() : Map.copyOf(inputItemData);
            inputAmount = Math.max(1, inputAmount);
        }

        private boolean hasInputSource() {
            return inputSource != null && !inputSource.isBlank();
        }
    }
}
