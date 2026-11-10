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

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
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
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;

public final class WokRuntimeService {

    static final ItemSourceRef BOWL_SOURCE = ItemSourceUtil.parse("minecraft-bowl");

    final EmakiCookingPlugin plugin;
    final MessageService messageService;
    final CookingSettingsService settingsService;
    final CookingBlockMatcher blockMatcher;
    final StationStateStore stateStore;
    final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    final ItemSourceService itemSourceService;
    final CookingDisplayService displayService;
    final CookingTextDisplayService textDisplayService;
    CookingCompletionCoordinator completionCoordinator;

    public WokRuntimeService(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            CookingDisplayService displayService,
            CookingTextDisplayService textDisplayService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.recipeService = recipeService;
        this.rewardService = rewardService;
        this.itemSourceService = itemSourceService;
        this.displayService = Objects.requireNonNull(displayService, "displayService");
        this.textDisplayService = Objects.requireNonNull(textDisplayService, "textDisplayService");
    }

    public void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) { new WokRuntimeServiceDelegate(this).setCompletionCoordinator(completionCoordinator); }

    CookingStationStateAccess completionStateAccess() {
        return new CookingStationStateAccess() {
            @Override
            public StationType stationType() {
                return StationType.WOK;
            }

            @Override
            public Map<String, Object> snapshot(StationCoordinates coordinates) {
                WokState state = readState(stateStore.load(coordinates));
                return state == null ? null : serializeState(coordinates, state);
            }

            @Override
            public CompletionStage<Void> replace(
                    StationCoordinates coordinates,
                    Map<String, Object> committedState) {
                WokState state = readState(new MapYamlSection(committedState));
                if (state == null) {
                    return CompletableFuture.failedFuture(
                            new IllegalArgumentException("已提交的炒锅状态无效"));
                }
                return stateStore.saveAsync(coordinates, committedState)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> refreshDisplays(coordinates, state)));
            }

            @Override
            public CompletionStage<Void> delete(StationCoordinates coordinates) {
                return stateStore.deleteAsync(coordinates)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> clearRuntimeVisuals(coordinates)));
            }
        };
    }

    public void reload() {
        displayService.removeStationType(StationType.WOK);
        stateStore.forEachLoadedState(StationType.WOK, this::restoreStoredState);
    }

    public boolean restoreStoredState(StationCoordinates coordinates, YamlSection section) { return new WokRuntimeServiceDelegate(this).restoreStoredState(coordinates, section); }

    public void unloadStoredState(StationCoordinates coordinates) { new WokRuntimeServiceDelegate(this).unloadStoredState(coordinates); }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand() || !blockMatcher.matches(interaction, StationType.WOK)) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.WOK_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, CommandKeys.NO_PERMISSION);
            interaction.cancel();
            return true;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.WOK, coordinates)) {
            interaction.cancel();
            return true;
        }
        WokState state = readState(stateStore.load(coordinates));
        int heatLevel = resolveHeatLevel(block.getRelative(BlockFace.DOWN));
        ItemStack hand = player.getInventory().getItemInMainHand();
        debugStation("station.wok_interact", Map.of(
                "player", player.getName(),
                "station", coordinates.runtimeKey(),
                "interaction", interaction.type() == null ? "none" : interaction.type().configKey(),
                "hand", hand == null || hand.getType().isAir() ? "air" : String.valueOf(hand.getType().getKey()),
                "hand_source", identifySource(hand),
                "is_spatula", isSpatula(hand),
                "is_bowl", isPlainBowl(hand),
                "heat", heatLevel,
                "state", state == null ? "null" : "ingredients=" + state.ingredients().size() + ",stir=" + state.totalStirCount()
        ));

        if (isSpatula(hand) && settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_INSPECT,
                interaction)) {
            return handleInspect(interaction, player, state, heatLevel);
        }

        if (isSpatula(hand)) {
            return handleStir(interaction, block, player, coordinates, state, heatLevel, hand);
        }

        boolean servingWithBowl = settingsService.wokNeedBowl() && isPlainBowl(hand);
        boolean servingWithEmptyHand = !settingsService.wokNeedBowl() && (hand == null || hand.getType().isAir());
        if ((servingWithBowl || servingWithEmptyHand) && settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_SERVE,
                interaction)
                && handleServe(interaction, block, player, coordinates, state, heatLevel, servingWithBowl)) {
            return true;
        }

        if (hand != null && !hand.getType().isAir()) {
            return handleAddIngredient(interaction, block, player, coordinates, state, heatLevel, hand);
        }

        return handleReturnIngredient(interaction, player, coordinates, state);
    }

    private boolean handleInspect(StationInteraction interaction,
            Player player,
            WokState state,
            int heatLevel) { return new WokRuntimeServiceDelegate(this).handleInspect(interaction, player, state, heatLevel); }

    private boolean handleStir(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            WokState state,
            int heatLevel,
            ItemStack hand) {
        if (!settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_STIR,
                interaction)) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.WOK_STIR)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, CommandKeys.NO_PERMISSION);
            interaction.cancel();
            return true;
        }
        if (state == null || !state.hasIngredients()) {
            debugStir("stir.rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "reason", "no_state"
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_item", Map.of());
            interaction.cancel();
            return true;
        }
        long now = System.currentTimeMillis();
        if (state.totalStirCount() > 0
                && settingsService.wokTimeoutMs() > 0L
                && now - state.lastStirTimeMs() > settingsService.wokTimeoutMs()) {
            debugStir("stir.rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "reason", "burnt_timeout elapsed=" + (now - state.lastStirTimeMs()) + "ms limit=" + settingsService.wokTimeoutMs() + "ms"
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.burnt_timeout", Map.of());
            interaction.cancel();
            return true;
        }
        if (state.lastStirActionMs() > 0L
                && settingsService.wokStirDelayMs() > 0L
                && now - state.lastStirActionMs() < settingsService.wokStirDelayMs()) {
            debugStir("stir.rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "reason", "too_fast elapsed=" + (now - state.lastStirActionMs()) + "ms required=" + settingsService.wokStirDelayMs() + "ms"
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.too_fast", Map.of());
            interaction.cancel();
            return true;
        }
        applyStir(interaction, block, player, coordinates, state, heatLevel, hand, now);
        return true;
    }

    private void applyStir(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            WokState state,
            int heatLevel,
            ItemStack hand,
            long now) {
        damageHeldTool(player, hand, 1);
        List<WokIngredientState> updatedIngredients = new ArrayList<>();
        for (WokIngredientState ingredient : state.ingredients()) {
            updatedIngredients.add(new WokIngredientState(
                    ingredient.source(),
                    ingredient.amount(),
                    ingredient.stirTimes() + 1,
                    ingredient.itemData()
            ));
        }
        WokState updated = new WokState(updatedIngredients, state.totalStirCount() + 1, now, now);
        RecipeDocument stirPredicted = predictRecipe(updated, heatLevel);
        debugStir("stir.trigger", Map.of(
                "player", player.getName(),
                "station", coordinates.runtimeKey(),
                "count", updated.totalStirCount(),
                "max", stirPredicted == null ? 0 : recipeService.wokStirTotalMax(stirPredicted)
        ));
        saveState(coordinates, updated);
        refreshText(coordinates, updated);
        Location particleLocation = block.getLocation().add(0.5D, 1.05D, 0.5D);
        if (particleLocation.getWorld() != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "execute in " + particleLocation.getWorld().getKey() + " run particle " + Particle.CLOUD.getKey()
                            + " " + particleLocation.getX() + " " + particleLocation.getY() + " " + particleLocation.getZ()
                            + " 0.15 0.1 0.15 0.01 4 force");
        }
        plugin.effectService().playActions(StationType.WOK, "stir", player);
        if (settingsService.wokStirAnimationEnabled() && !displayService.isAnimating(StationType.WOK, coordinates)) {
            displayService.playStirAnimation(
                    StationType.WOK,
                    coordinates,
                    settingsService.wokStirAnimationHeight(),
                    settingsService.wokStirAnimationAxis(),
                    settingsService.wokStirAnimationRotation(),
                    settingsService.wokStirAnimationDurationTicks()
            );
        }
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.stir_count", Map.of("count", updated.totalStirCount()));
        interaction.cancel();
    }

    private boolean handleServe(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            WokState state,
            int heatLevel,
            boolean servingWithBowl) { return new WokRuntimeServiceDelegate(this).handleServe(interaction, block, player, coordinates, state, heatLevel, servingWithBowl); }

    private boolean handleAddIngredient(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            WokState state,
            int heatLevel,
            ItemStack hand) { return new WokRuntimeServiceDelegate(this).handleAddIngredient(interaction, block, player, coordinates, state, heatLevel, hand); }

    boolean addFirstIngredient(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            WokState state,
            int heatLevel,
            String source,
            ItemStack hand) {
        if (heatLevel <= 0) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_heat", Map.of());
            interaction.cancel();
            return true;
        }
        if (settingsService.onlyRecipeItems(StationType.WOK)
                && !recipeService.canAcceptWokIngredientPrefix(candidateIngredients(state, source, hand), player, heatLevel)) {
            debugStation("station.ingredient_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "item", source,
                    "reason", "first_ingredient_not_in_any_recipe",
                    "candidates", String.valueOf(candidateIngredients(state, source, hand)),
                    "heat", heatLevel
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "general.input_rejected", Map.of());
            interaction.cancel();
            return true;
        }
        ItemStack consumed = CookingRuntimeUtil.takeOneFromMainHand(player);
        if (consumed == null || consumed.getType().isAir()) {
            return false;
        }
        WokState created = new WokState(List.of(new WokIngredientState(
                source,
                1,
                0,
                List.of(StoredItemCodec.serialize(consumed))
        )), 0, 0L, 0L);
        saveState(coordinates, created);
        refreshDisplays(coordinates, created);
        setWokHeatSourceLit(block, true);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.ingredient_added", Map.of("item", itemDisplayName(source)));
        plugin.effectService().playActions(StationType.WOK, "add_ingredient", player);
        interaction.cancel();
        return true;
    }

    boolean appendIngredient(StationInteraction interaction,
            Block block,
            Player player,
            StationCoordinates coordinates,
            WokState state,
            int heatLevel,
            String source,
            ItemStack hand) {
        if (settingsService.onlyRecipeItems(StationType.WOK)
                && !recipeService.canAcceptWokIngredientPrefix(candidateIngredients(state, source, hand), player, heatLevel)) {
            debugStation("station.ingredient_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "item", source,
                    "reason", "prefix_not_in_any_recipe",
                    "candidates", String.valueOf(candidateIngredients(state, source, hand)),
                    "heat", heatLevel
            ));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "general.input_rejected", Map.of());
            interaction.cancel();
            return true;
        }
        ItemStack consumed = CookingRuntimeUtil.takeOneFromMainHand(player);
        if (consumed == null || consumed.getType().isAir()) {
            return false;
        }
        List<WokIngredientState> updatedIngredients = new ArrayList<>(state.ingredients());
        int lastIndex = updatedIngredients.size() - 1;
        if (lastIndex >= 0 && sourceMatches(updatedIngredients.get(lastIndex).source(), source)) {
            WokIngredientState last = updatedIngredients.get(lastIndex);
            updatedIngredients.set(lastIndex, new WokIngredientState(
                    last.source(),
                    last.amount() + 1,
                    last.stirTimes(),
                    appendItemData(last.itemData(), StoredItemCodec.serialize(consumed))
            ));
        } else {
            updatedIngredients.add(new WokIngredientState(source, 1, 0, List.of(StoredItemCodec.serialize(consumed))));
        }
        WokState updated = new WokState(updatedIngredients, state.totalStirCount(), state.lastStirTimeMs(), state.lastStirActionMs());
        saveState(coordinates, updated);
        refreshDisplays(coordinates, updated);
        setWokHeatSourceLit(block, true);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.ingredient_added", Map.of("item", itemDisplayName(source)));
        plugin.effectService().playActions(StationType.WOK, "add_ingredient", player);
        interaction.cancel();
        return true;
    }

    private boolean handleReturnIngredient(StationInteraction interaction,
            Player player,
            StationCoordinates coordinates,
            WokState state) { return new WokRuntimeServiceDelegate(this).handleReturnIngredient(interaction, player, coordinates, state); }

    public boolean handleBreak(StationBreakContext context) { return new WokRuntimeServiceDelegate(this).handleBreak(context); }

    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) { return new WokRuntimeServiceDelegate(this).snapshotAt(coordinates); }

    void showContents(Player player, WokState state, int heatLevel) { new WokRuntimeServiceDelegate(this).showContents(player, state, heatLevel); }

    boolean tryServe(Player player,
            Block block,
            StationCoordinates coordinates,
            WokState state,
            int heatLevel,
            boolean consumeBowl) {
        if (state.totalStirCount() <= 0) {
            return false;
        }
        RecipeDocument recipe = findMatchingRecipe(state, player, heatLevel);
        if (recipe == null) {
            if (!settingsService.wokFailureEnabled() || Texts.isBlank(settingsService.wokFailureOutputSource())) {
                return false;
            }
            completeWithCustomSource(
                    player,
                    block,
                    coordinates,
                    state,
                    consumeBowl,
                    settingsService.wokFailureOutputSource(),
                    settingsService.wokDropResult(),
                    "cooking_wok_invalid",
                    "wok.completed_invalid",
                    Map.of("recipe", "invalid")
            );
            return true;
        }

        String branch = determineOutcomeBranch(state, recipe);
        debugStation("station.wok_outcome", Map.of(
                "station", coordinates.runtimeKey(),
                "recipe", recipe.id(),
                "branch", branch,
                "stir", state.totalStirCount(),
                "stir_min", recipeService.wokStirTotalMin(recipe),
                "stir_max", recipeService.wokStirTotalMax(recipe),
                "tolerance", recipeService.wokFaultTolerance(recipe)
        ));
        Map<String, Object> outcome = switch (branch) {
            case "success" -> recipeService.outcome(recipe, "result.success");
            case "undercooked" -> recipeService.outcome(recipe, "result.undercooked");
            case "overcooked" -> recipeService.outcome(recipe, "result.overcooked");
            default -> invalidOutcome(recipe);
        };
        Location rewardLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
        Map<String, Object> completionPlaceholders = Map.of(
                "recipe_id", recipe.id(),
                "station_type", StationType.WOK.folderName(),
                "outcome", branch
        );

        CookingRewardService.ConditionGate gate = rewardService.evaluateConditionGate(recipe, player);
        if (gate.blocked()) {
            debugStation("station.wok_serve", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "result", "rejected_condition",
                    "with_bowl", consumeBowl,
                    "stir", state.totalStirCount(),
                    "recipe", recipe.id()
            ));
            rewardService.runConditionFailActions(
                    gate.failActions(),
                    player,
                    rewardLocation,
                    "cooking_wok_" + branch,
                    completionPlaceholders
            );
            return true;
        }
        CookingCompletionRequest.PlayerInventoryInput bowlInput = consumeBowl
                ? CookingCompletionRequest.PlayerInventoryInput.mainHand(player, 1, "wok serving bowl")
                : null;
        boolean accepted = (!consumeBowl || bowlInput != null)
                && completionCoordinator != null
                && completionCoordinator.submit(new CookingCompletionRequest(
                        "serve:" + state.lastStirTimeMs() + ":" + state.totalStirCount() + ":" + branch,
                        StationType.WOK,
                        coordinates,
                        serializeState(coordinates, state),
                        CookingCompletionOperation.CommitMode.DELETE,
                        Map.of(),
                        recipe,
                        player,
                        rewardLocation,
                        settingsService.wokDropResult(),
                        state.ingredients().stream()
                                .map(ingredient -> new CookingInputIngredient(ingredient.source(), ingredient.amount()))
                                .toList(),
                        recipeService.outputs(outcome),
                        recipeService.actions(outcome),
                        "cooking_wok_" + branch,
                        completionPlaceholders,
                        bowlInput == null ? List.of() : List.of(bowlInput),
                        gate.outcome()
                ));
        debugStation("station.wok_serve", Map.of(
                "player", player.getName(),
                "station", coordinates.runtimeKey(),
                "result", accepted ? "submitted" : "submit_rejected",
                "with_bowl", consumeBowl,
                "stir", state.totalStirCount(),
                "recipe", recipe.id()
        ));
        if (!accepted) {
            return false;
        }
        switch (branch) {
            case "success" -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_success", Map.of("recipe", recipe.displayName()));
            case "undercooked" -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_undercooked", Map.of("recipe", recipe.displayName()));
            case "overcooked" -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_overcooked", Map.of("recipe", recipe.displayName()));
            default -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_invalid", Map.of("recipe", recipe.displayName()));
        }
        plugin.effectService().playActions(StationType.WOK, "serve", player);
        return true;
    }

    private Map<String, Object> invalidOutcome(RecipeDocument recipe) { return new WokRuntimeServiceDelegate(this).invalidOutcome(recipe); }

    private void completeWithCustomSource(Player player,
            Block block,
            StationCoordinates coordinates,
            WokState state,
            boolean consumeBowl,
            String source,
            boolean dropResult,
            String phase,
            String messageKey,
            Map<String, ?> replacements) { new WokRuntimeServiceDelegate(this).completeWithCustomSource(player, block, coordinates, state, consumeBowl, source, dropResult, phase, messageKey, replacements); }

    String determineOutcomeBranch(WokState state, RecipeDocument recipe) { return new WokRuntimeServiceDelegate(this).determineOutcomeBranch(state, recipe); }

    RecipeDocument findMatchingRecipe(WokState state, Player player, int heatLevel) { return new WokRuntimeServiceDelegate(this).findMatchingRecipe(state, player, heatLevel); }

    WokIngredientState returnLastIngredient(Player player, StationCoordinates coordinates, WokState state) { return new WokRuntimeServiceDelegate(this).returnLastIngredient(player, coordinates, state); }

    int resolveHeatLevel(Block block) { return new WokRuntimeServiceDelegate(this).resolveHeatLevel(block); }

    boolean matchesHeatLevelRule(Block block, CookingSettingsService.HeatLevelRule rule) { return new WokRuntimeServiceDelegate(this).matchesHeatLevelRule(block, rule); }

    void setWokHeatSourceLit(Block wokBlock, boolean lit) { new WokRuntimeServiceDelegate(this).setWokHeatSourceLit(wokBlock, lit); }

    boolean applyConfiguredHeatSourceTransition(Block block, boolean lit) { return new WokRuntimeServiceDelegate(this).applyConfiguredHeatSourceTransition(block, lit); }

    boolean matchesSource(Block block, ItemSourceRef source) { return new WokRuntimeServiceDelegate(this).matchesSource(block, source); }

    private boolean isSpatula(ItemStack itemStack) { return new WokRuntimeServiceDelegate(this).isSpatula(itemStack); }

    private boolean isPlainBowl(ItemStack itemStack) { return new WokRuntimeServiceDelegate(this).isPlainBowl(itemStack); }

    String identifySource(ItemStack itemStack) { return new WokRuntimeServiceDelegate(this).identifySource(itemStack); }

    String itemDisplayName(String source) { return new WokRuntimeServiceDelegate(this).itemDisplayName(source); }

    String describeRecipe(RecipeDocument recipe) { return new WokRuntimeServiceDelegate(this).describeRecipe(recipe); }

    private void damageHeldTool(Player player, ItemStack itemStack, int amount) { new WokRuntimeServiceDelegate(this).damageHeldTool(player, itemStack, amount); }

    void saveState(StationCoordinates coordinates, WokState state) { new WokRuntimeServiceDelegate(this).saveState(coordinates, state); }

    void debugStation(String langKey, Map<String, ?> replacements) { new WokRuntimeServiceDelegate(this).debugStation(langKey, replacements); }

    private void debugStir(String langKey, Map<String, ?> replacements) { new WokRuntimeServiceDelegate(this).debugStir(langKey, replacements); }

    Map<String, Object> serializeState(StationCoordinates coordinates, WokState state) { return new WokRuntimeServiceDelegate(this).serializeState(coordinates, state); }

    void refreshText(StationCoordinates coordinates, WokState state) {
        if (!settingsService.textDisplayEnabled(StationType.WOK)
                || coordinates == null || state == null || !state.hasIngredients()) {
            textDisplayService.removeStation(StationType.WOK, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.WOK, coordinates);
            return;
        }
        Block block = coordinates.block();
        int heatLevel = block == null ? 0 : resolveHeatLevel(block.getRelative(BlockFace.DOWN));

        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.wok.title"));
        appendLine(builder, messageService.message("text_display.wok.heat", Map.of("heat", heatLevel)));
        appendLine(builder, messageService.message("text_display.wok.stir", Map.of("count", state.totalStirCount())));
        appendLine(builder, messageService.message("text_display.wok.ingredients_header"));
        for (WokIngredientState ingredient : state.ingredients()) {
            appendLine(builder, messageService.message("text_display.wok.ingredient_line", Map.of(
                    "item", itemDisplayName(ingredient.source()),
                    "amount", ingredient.amount()
            )));
        }
        RecipeDocument predicted = predictRecipe(state, heatLevel);
        if (predicted == null) {
            appendLine(builder, messageService.message("text_display.wok.no_predict"));
        } else {
            appendLine(builder, messageService.message("text_display.wok.predicted", Map.of("recipe", predicted.displayName())));
            appendLine(builder, nextStepHint(state, predicted));
        }
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.WOK,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.WOK)
        ));
    }

    RecipeDocument predictRecipe(WokState state, int heatLevel) { return new WokRuntimeServiceDelegate(this).predictRecipe(state, heatLevel); }

    boolean ingredientsMatchRecipePrefix(RecipeDocument recipe, List<CookingRecipeService.WokIngredientInput> actual) { return new WokRuntimeServiceDelegate(this).ingredientsMatchRecipePrefix(recipe, actual); }

    String nextStepHint(WokState state, RecipeDocument recipe) { return new WokRuntimeServiceDelegate(this).nextStepHint(state, recipe); }

    private void appendLine(StringBuilder builder, String line) { new WokRuntimeServiceDelegate(this).appendLine(builder, line); }

    void refreshDisplays(StationCoordinates coordinates, WokState state) {
        displayService.removeStation(StationType.WOK, coordinates);
        refreshText(coordinates, state);
        if (coordinates == null || state == null || !state.hasIngredients()) {
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            return;
        }
        List<WokIngredientDisplay> displayIngredients = displayIngredients(state);
        int count = displayIngredients.size();
        if (count <= 0) {
            return;
        }
        for (int index = 0; index < count; index++) {
            WokIngredientDisplay ingredient = displayIngredients.get(index);
            ItemStack itemStack = ingredient.itemStack();
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSourceRef source = ItemSourceUtil.parse(ingredient.source());
            if (source == null) {
                source = itemSourceService.identifyItem(itemStack);
            }
            if (source == null) {
                continue;
            }
            CookingSettingsService.DisplayAdjustmentProfile adjustment = settingsService.displayAdjustment(
                    StationType.WOK,
                    source,
                    itemStack.getType().isBlock()
            );
            displayService.upsert(new CookingDisplaySpec(
                    StationType.WOK,
                    coordinates,
                    "ingredient_" + index,
                    itemStack,
                    baseLocation,
                    adjustment,
                    layoutOffset(index, count)
            ));
        }
    }

    List<WokIngredientDisplay> displayIngredients(WokState state) { return new WokRuntimeServiceDelegate2(this).displayIngredients(state); }

    String displayGroupKey(String source) { return new WokRuntimeServiceDelegate2(this).displayGroupKey(source); }

    ItemStack displayItem(WokIngredientState ingredient) { return new WokRuntimeServiceDelegate2(this).displayItem(ingredient); }

    boolean isDisplayable(ItemStack itemStack) { return new WokRuntimeServiceDelegate2(this).isDisplayable(itemStack); }

    private CookingSettingsService.Vector3 layoutOffset(int index, int count) { return new WokRuntimeServiceDelegate2(this).layoutOffset(index, count); }

    void clearState(StationCoordinates coordinates) { new WokRuntimeServiceDelegate2(this).clearState(coordinates); }

    void clearRuntimeVisuals(StationCoordinates coordinates) { new WokRuntimeServiceDelegate2(this).clearRuntimeVisuals(coordinates); }

    WokState readState(YamlSection section) { return new WokRuntimeServiceDelegate2(this).readState(section); }

    boolean sourceMatches(String left, String right) { return new WokRuntimeServiceDelegate2(this).sourceMatches(left, right); }

    String resolveIngredientSource(Map<String, Object> ingredient) { return new WokRuntimeServiceDelegate2(this).resolveIngredientSource(ingredient); }

    List<CookingRecipeService.WokIngredientInput> candidateIngredients(WokState state, String source, ItemStack hand) { return new WokRuntimeServiceDelegate2(this).candidateIngredients(state, source, hand); }

    private List<Map<String, Object>> appendItemData(List<Map<String, Object>> current, Map<String, Object> itemData) { return new WokRuntimeServiceDelegate2(this).appendItemData(current, itemData); }

    List<Map<String, Object>> removeLastItemData(List<Map<String, Object>> current) { return new WokRuntimeServiceDelegate2(this).removeLastItemData(current); }

    List<Map<String, Object>> readItemDataList(Object raw) { return new WokRuntimeServiceDelegate2(this).readItemDataList(raw); }

    record WokIngredientDisplay(String source, ItemStack itemStack) {
    }

    record WokState(List<WokIngredientState> ingredients,
            int totalStirCount,
            long lastStirTimeMs,
            long lastStirActionMs) {

        WokState {
            ingredients = ingredients == null || ingredients.isEmpty() ? List.of() : List.copyOf(ingredients);
        }

        boolean hasIngredients() {
            return !ingredients.isEmpty();
        }
    }

    record WokIngredientState(String source,
            int amount,
            int stirTimes,
            List<Map<String, Object>> itemData) {

        WokIngredientState {
            itemData = itemData == null || itemData.isEmpty()
                    ? List.of()
                    : itemData.stream()
                            .filter(entry -> entry != null && !entry.isEmpty())
                            .map(Map::copyOf)
                            .toList();
            amount = Math.max(Math.max(1, amount), itemData.size());
        }

        ItemStack lastStoredItem() {
            if (itemData.isEmpty()) {
                return null;
            }
            return StoredItemCodec.deserialize(itemData.getLast());
        }
    }
}
