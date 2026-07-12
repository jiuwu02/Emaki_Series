package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
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
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
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

public final class WokRuntimeService {

    private static final ItemSource BOWL_SOURCE = ItemSourceUtil.parse("minecraft-bowl");

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final CookingDisplayService displayService;
    private final CookingTextDisplayService textDisplayService;

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

    public void reload() {
        displayService.removeStationType(StationType.WOK);
        stateStore.forEachLoadedState(StationType.WOK, this::restoreStoredState);
    }

    public boolean restoreStoredState(StationCoordinates coordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        WokState state = readState(section);
        ItemSource stationSource = stateStore.stationSource(section);
        Block block = coordinates.block();
        if (state == null || !state.hasIngredients()) {
            displayService.removeStation(StationType.WOK, coordinates);
            textDisplayService.removeStation(StationType.WOK, coordinates);
            return false;
        }
        if (!blockMatcher.matches(block, StationType.WOK, stationSource)) {
            displayService.removeStation(StationType.WOK, coordinates);
            textDisplayService.removeStation(StationType.WOK, coordinates);
            plugin.getLogger().warning("Station restore report: skipped_mismatch type=wok coordinate=" + coordinates.runtimeKey());
            return false;
        }
        refreshDisplays(coordinates, state);
        return true;
    }

    public void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        displayService.removeStation(StationType.WOK, coordinates);
        textDisplayService.removeStation(StationType.WOK, coordinates);
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand() || !blockMatcher.matches(interaction, StationType.WOK)) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.WOK_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        WokState state = readState(stateStore.load(coordinates));
        int heatLevel = resolveHeatLevel(block.getRelative(BlockFace.DOWN));
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (isSpatula(hand) && settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_INSPECT,
                interaction)) {
            if (state == null || !state.hasIngredients()) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_item", Map.of());
                interaction.cancel();
                return true;
            }
            showContents(player, state, heatLevel);
            interaction.cancel();
            return true;
        }

        if (isSpatula(hand)) {
            if (!settingsService.matchesInteraction(
                    StationType.WOK,
                    CookingSettingsService.INTERACTION_STIR,
                    interaction)) {
                return false;
            }
            if (!player.hasPermission(CookingPermissions.WOK_STIR)
                    && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                interaction.cancel();
                return true;
            }
            if (state == null || !state.hasIngredients()) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_item", Map.of());
                interaction.cancel();
                return true;
            }
            long now = System.currentTimeMillis();
            if (state.totalStirCount() > 0
                    && settingsService.wokTimeoutMs() > 0L
                    && now - state.lastStirTimeMs() > settingsService.wokTimeoutMs()) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.burnt_timeout", Map.of());
                interaction.cancel();
                return true;
            }
            if (state.lastStirActionMs() > 0L
                    && settingsService.wokStirDelayMs() > 0L
                    && now - state.lastStirActionMs() < settingsService.wokStirDelayMs()) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.too_fast", Map.of());
                interaction.cancel();
                return true;
            }
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
            saveState(coordinates, updated);
            refreshText(coordinates, updated);
            Location particleLocation = block.getLocation().add(0.5D, 1.05D, 0.5D);
            if (particleLocation.getWorld() != null) {
                particleLocation.getWorld().spawnParticle(Particle.CLOUD, particleLocation, 4, 0.15D, 0.1D, 0.15D, 0.01D);
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
            return true;
        }

        boolean servingWithBowl = settingsService.wokNeedBowl() && isPlainBowl(hand);
        boolean servingWithEmptyHand = !settingsService.wokNeedBowl() && (hand == null || hand.getType().isAir());
        if ((servingWithBowl || servingWithEmptyHand) && settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_SERVE,
                interaction)) {
            if (!player.hasPermission(CookingPermissions.WOK_SERVE)
                    && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                interaction.cancel();
                return true;
            }
            if (state == null || !state.hasIngredients()) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_item", Map.of());
                interaction.cancel();
                return true;
            }
            if (tryServe(player, block, coordinates, state, heatLevel, servingWithBowl)) {
                interaction.cancel();
                return true;
            }
            if (servingWithBowl) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_recipe", Map.of());
                interaction.cancel();
                return true;
            }
        }

        if (hand != null && !hand.getType().isAir()) {
            if (!settingsService.matchesInteraction(
                    StationType.WOK,
                    CookingSettingsService.INTERACTION_ADD_INGREDIENT,
                    interaction)) {
                return false;
            }
            String source = identifySource(hand);
            if (Texts.isBlank(source)) {
                return false;
            }
            if (state == null || !state.hasIngredients()) {
                if (heatLevel <= 0) {
                    CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_heat", Map.of());
                    interaction.cancel();
                    return true;
                }
                if (settingsService.onlyRecipeItems(StationType.WOK)
                        && !recipeService.canAcceptWokIngredientPrefix(candidateIngredients(state, source), player, heatLevel)) {
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

            if (settingsService.onlyRecipeItems(StationType.WOK)
                    && !recipeService.canAcceptWokIngredientPrefix(candidateIngredients(state, source), player, heatLevel)) {
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

        if (state == null || !state.hasIngredients()) {
            return false;
        }
        if (!settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_RETURN_INGREDIENT,
                interaction)) {
            return false;
        }
        if (state.totalStirCount() > 0 && settingsService.wokScaldDamageEnabled()) {
            int damage = settingsService.wokScaldDamageValue();
            if (damage > 0) {
                player.damage(damage);
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.scalded", Map.of("damage", damage));
            }
        }
        WokIngredientState removed = returnLastIngredient(player, coordinates, state);
        if (removed != null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.ingredient_returned", Map.of("item", itemDisplayName(removed.source())));
            interaction.cancel();
            return true;
        }
        return false;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(context, StationType.WOK)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, context.stationSource());
        WokState state = readState(stateStore.load(coordinates));
        if (state == null || !state.hasIngredients()) {
            return false;
        }
        Location dropLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (WokIngredientState ingredient : state.ingredients()) {
            if (dropLocation.getWorld() == null) {
                continue;
            }
            int droppedStoredItems = 0;
            for (Map<String, Object> itemData : ingredient.itemData()) {
                ItemStack storedItem = StoredItemCodec.deserialize(itemData);
                if (storedItem == null || storedItem.getType().isAir()) {
                    continue;
                }
                dropLocation.getWorld().dropItemNaturally(dropLocation, storedItem);
                droppedStoredItems++;
            }
            int fallbackAmount = Math.max(0, ingredient.amount() - droppedStoredItems);
            if (fallbackAmount > 0) {
                ItemSource source = ItemSourceUtil.parse(ingredient.source());
                ItemStack itemStack = source == null ? null : itemSourceService.createItem(source, fallbackAmount);
                if (itemStack != null && !itemStack.getType().isAir()) {
                    dropLocation.getWorld().dropItemNaturally(dropLocation, itemStack);
                }
            }
        }
        clearState(coordinates);
        return true;
    }

    /**
     * 构建炒锅运行态快照。无食材时返回空。火力为下方热源实时计算值，不持久化。
     */
    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        WokState state = readState(stateStore.load(coordinates));
        if (state == null || !state.hasIngredients()) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        int heatLevel = block == null ? 0 : resolveHeatLevel(block.getRelative(BlockFace.DOWN));
        WokIngredientState first = state.ingredients().get(0);
        RecipeDocument recipe = predictRecipe(state, heatLevel);
        int target = recipe == null ? 0 : recipeService.wokStirTotalMin(recipe);
        int current = state.totalStirCount();
        double percent = target > 0 ? Math.min(100.0D, (double) current * 100.0D / (double) target) : 0.0D;
        return Optional.of(new StationSnapshot(
                StationType.WOK,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(plugin, block),
                CookingRuntimeUtil.resolveBlockId(plugin, block == null ? null : block.getRelative(BlockFace.DOWN)),
                false,
                0L,
                heatLevel, 0, 0,
                MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(first.source())),
                first.source(),
                first.amount(),
                state.ingredients().size(),
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

    private void showContents(Player player, WokState state, int heatLevel) {
        messageService.sendRaw(player, messageService.message("wok.info_header"));
        int index = 1;
        for (WokIngredientState ingredient : state.ingredients()) {
            messageService.sendRaw(player, messageService.message("wok.info_line", Map.of(
                    "index", index,
                    "item", itemDisplayName(ingredient.source()),
                    "amount", ingredient.amount(),
                    "stir", ingredient.stirTimes()
            )));
            index++;
        }
        messageService.sendRaw(player, messageService.message("wok.info_footer", Map.of(
                "count", state.totalStirCount(),
                "heat", heatLevel
        )));
    }

    private boolean tryServe(Player player,
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
            if (consumeBowl) {
                CookingRuntimeUtil.takeOneFromMainHand(player);
            }
            completeWithCustomSource(
                    player,
                    block,
                    coordinates,
                    settingsService.wokFailureOutputSource(),
                    settingsService.wokDropResult(),
                    "cooking_wok_invalid",
                    "wok.completed_invalid",
                    Map.of("recipe", "invalid")
            );
            return true;
        }

        String branch = determineOutcomeBranch(state, recipe);
        Map<String, Object> outcome = switch (branch) {
            case "success" -> recipeService.outcome(recipe, "result.success");
            case "undercooked" -> recipeService.outcome(recipe, "result.undercooked");
            case "overcooked" -> recipeService.outcome(recipe, "result.overcooked");
            default -> invalidOutcome(recipe);
        };
        if (consumeBowl) {
            CookingRuntimeUtil.takeOneFromMainHand(player);
        }
        clearState(coordinates);
        rewardService.deliver(
                recipe,
                player,
                block.getLocation().add(0.5D, 1.0D, 0.5D),
                settingsService.wokDropResult(),
                state.ingredients().stream()
                        .map(ingredient -> new CookingInputIngredient(ingredient.source(), ingredient.amount()))
                        .toList(),
                recipeService.outputs(outcome),
                recipeService.actions(outcome),
                "cooking_wok_" + branch,
                Map.of(
                        "recipe_id", recipe.id(),
                        "station_type", StationType.WOK.folderName(),
                        "outcome", branch
                )
        );
        switch (branch) {
            case "success" -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_success", Map.of("recipe", recipe.displayName()));
            case "undercooked" -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_undercooked", Map.of("recipe", recipe.displayName()));
            case "overcooked" -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_overcooked", Map.of("recipe", recipe.displayName()));
            default -> CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.completed_invalid", Map.of("recipe", recipe.displayName()));
        }
        plugin.effectService().playActions(StationType.WOK, "serve", player);
        return true;
    }

    private Map<String, Object> invalidOutcome(RecipeDocument recipe) {
        Map<String, Object> configured = recipeService.outcome(recipe, "result.invalid");
        if (!configured.isEmpty()) {
            return configured;
        }
        if (Texts.isNotBlank(settingsService.wokInvalidResultSource())) {
            return Map.of("item_sources", List.of(settingsService.wokInvalidResultSource()), "amount", 1);
        }
        if (Texts.isNotBlank(settingsService.wokFailureOutputSource())) {
            return Map.of("item_sources", List.of(settingsService.wokFailureOutputSource()), "amount", 1);
        }
        return Map.of();
    }

    private void completeWithCustomSource(Player player,
            Block block,
            StationCoordinates coordinates,
            String source,
            boolean dropResult,
            String phase,
            String messageKey,
            Map<String, ?> replacements) {
        clearState(coordinates);
        rewardService.deliver(
                null,
                player,
                block.getLocation().add(0.5D, 1.0D, 0.5D),
                dropResult,
                List.of(),
                List.of(Map.of("item_sources", List.of(source), "amount", 1)),
                List.of(),
                phase,
                Map.of("station_type", StationType.WOK.folderName())
        );
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, messageKey, replacements);
    }

    private String determineOutcomeBranch(WokState state, RecipeDocument recipe) {
        long now = System.currentTimeMillis();
        if (state.lastStirTimeMs() > 0L
                && settingsService.wokTimeoutMs() > 0L
                && now - state.lastStirTimeMs() > settingsService.wokTimeoutMs()) {
            return "overcooked";
        }
        if (state.totalStirCount() < recipeService.wokStirTotalMin(recipe)) {
            return "undercooked";
        }
        if (state.totalStirCount() > recipeService.wokStirTotalMax(recipe)) {
            return "overcooked";
        }
        if (settingsService.wokFailureEnabled()
                && settingsService.wokFailureChance() > 0
                && ThreadLocalRandom.current().nextInt(100) < settingsService.wokFailureChance()) {
            return "invalid";
        }

        int lessThan = 0;
        int greaterThan = 0;
        List<Map<String, Object>> expected = recipeService.wokIngredients(recipe);
        for (int index = 0; index < Math.min(expected.size(), state.ingredients().size()); index++) {
            Map<String, Object> ingredient = expected.get(index);
            int actualStirs = state.ingredients().get(index).stirTimes();
            int comparison = recipeService.compareWokStirRule(String.valueOf(ingredient.getOrDefault("stir_rule", "0")), actualStirs);
            if (comparison < 0) {
                lessThan++;
            } else if (comparison > 0) {
                greaterThan++;
            }
        }
        int mismatchCount = lessThan + greaterThan;
        if (mismatchCount <= recipeService.wokFaultTolerance(recipe)) {
            return "success";
        }
        if (greaterThan > lessThan) {
            return "overcooked";
        }
        if (lessThan > greaterThan) {
            return "undercooked";
        }
        return "invalid";
    }

    private RecipeDocument findMatchingRecipe(WokState state, Player player, int heatLevel) {
        for (RecipeDocument recipe : recipeService.wokRecipes()) {
            if (recipe == null) {
                continue;
            }
            if (!recipeService.canUseRecipe(recipe, player)) {
                continue;
            }
            if (recipeService.wokHeatLevel(recipe) > 0 && recipeService.wokHeatLevel(recipe) != heatLevel) {
                continue;
            }
            List<Map<String, Object>> expected = recipeService.wokIngredients(recipe);
            if (expected.size() != state.ingredients().size()) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < expected.size(); index++) {
                Map<String, Object> ingredient = expected.get(index);
                String expectedSource = resolveIngredientSource(ingredient);
                int expectedAmount = CookingRuntimeUtil.parseInteger(ingredient.get("amount"), 1);
                WokIngredientState actual = state.ingredients().get(index);
                if (!sourceMatches(expectedSource, actual.source()) || expectedAmount != actual.amount()) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return recipe;
            }
        }
        return null;
    }

    private WokIngredientState returnLastIngredient(Player player, StationCoordinates coordinates, WokState state) {
        if (state == null || !state.hasIngredients()) {
            return null;
        }
        List<WokIngredientState> updatedIngredients = new ArrayList<>(state.ingredients());
        int lastIndex = updatedIngredients.size() - 1;
        if (lastIndex < 0) {
            return null;
        }
        WokIngredientState last = updatedIngredients.get(lastIndex);
        ItemStack itemStack = last.lastStoredItem();
        if (itemStack == null || itemStack.getType().isAir()) {
            ItemSource source = ItemSourceUtil.parse(last.source());
            itemStack = source == null ? null : itemSourceService.createItem(source, 1);
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        InventoryItemUtil.giveOrDrop(player, itemStack);
        if (last.amount() <= 1) {
            updatedIngredients.remove(lastIndex);
        } else {
            updatedIngredients.set(lastIndex, new WokIngredientState(
                    last.source(),
                    last.amount() - 1,
                    last.stirTimes(),
                    removeLastItemData(last.itemData())
            ));
        }
        if (updatedIngredients.isEmpty()) {
            clearState(coordinates);
        } else {
            WokState updated = new WokState(updatedIngredients, state.totalStirCount(), state.lastStirTimeMs(), state.lastStirActionMs());
            saveState(coordinates, updated);
            refreshDisplays(coordinates, updated);
        }
        return last;
    }

    private int resolveHeatLevel(Block block) {
        if (block == null) {
            return 0;
        }
        int resolved = 0;
        for (CookingSettingsService.HeatLevelRule rule : settingsService.wokHeatLevels()) {
            if (rule == null || rule.source() == null) {
                continue;
            }
            if (matchesHeatLevelRule(block, rule)) {
                resolved = Math.max(resolved, rule.level());
            }
        }
        return resolved;
    }

    private boolean matchesHeatLevelRule(Block block, CookingSettingsService.HeatLevelRule rule) {
        return rule != null
                && (matchesSource(block, rule.source())
                || matchesSource(block, rule.litSource())
                || matchesSource(block, rule.unlitSource()));
    }

    private void setWokHeatSourceLit(Block wokBlock, boolean lit) {
        if (!settingsService.wokIgniteHeatSource() || wokBlock == null) {
            return;
        }
        Block heatSourceBlock = wokBlock.getRelative(BlockFace.DOWN);
        if (heatSourceBlock == null || resolveHeatLevel(heatSourceBlock) <= 0) {
            return;
        }
        boolean directStateChanged = false;
        BlockData blockData = heatSourceBlock.getBlockData();
        if (blockData instanceof Lightable lightable) {
            lightable.setLit(lit);
            heatSourceBlock.setBlockData(lightable);
            directStateChanged = true;
        }
        if (heatSourceBlock.getState() instanceof Furnace furnace) {
            furnace.setBurnTime((short) (lit ? Short.MAX_VALUE : 0));
            furnace.update();
            directStateChanged = true;
        }
        if (!directStateChanged && !blockMatcher.setCustomLit(heatSourceBlock, lit)) {
            applyConfiguredHeatSourceTransition(heatSourceBlock, lit);
        }
    }

    private boolean applyConfiguredHeatSourceTransition(Block block, boolean lit) {
        if (block == null) {
            return false;
        }
        for (CookingSettingsService.HeatLevelRule rule : settingsService.wokHeatLevels()) {
            if (rule == null) {
                continue;
            }
            ItemSource target = lit ? rule.litSource() : rule.unlitSource();
            if (target == null) {
                continue;
            }
            if (blockMatcher.matches(block, target)) {
                return true;
            }
            if (lit && (matchesSource(block, rule.source()) || matchesSource(block, rule.unlitSource()))) {
                return blockMatcher.place(block, target);
            }
            if (!lit && matchesSource(block, rule.litSource())) {
                return blockMatcher.place(block, target);
            }
        }
        return false;
    }

    private boolean matchesSource(Block block, ItemSource source) {
        return block != null && source != null && blockMatcher.matches(block, source);
    }

    private boolean isSpatula(ItemStack itemStack) {
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        if (source == null) {
            return false;
        }
        for (ItemSource tool : settingsService.wokSpatulaSources()) {
            if (ItemSourceUtil.matches(tool, source)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlainBowl(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemSource source = itemSourceService.identifyItem(itemStack);
        return ItemSourceUtil.matches(BOWL_SOURCE, source);
    }

    private String identifySource(ItemStack itemStack) {
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    private String itemDisplayName(String source) {
        String displayName = EmakiCoreLibApi.itemDisplayName(source);
        return Texts.isBlank(displayName) ? source : displayName;
    }

    private void damageHeldTool(Player player, ItemStack itemStack, int amount) {
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

    private void saveState(StationCoordinates coordinates, WokState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.WOK, coordinates);

        List<Map<String, Object>> ingredients = new ArrayList<>();
        for (WokIngredientState ingredient : state.ingredients()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("source", ingredient.source());
            entry.put("amount", ingredient.amount());
            entry.put("stir_times", ingredient.stirTimes());
            if (!ingredient.itemData().isEmpty()) {
                entry.put("items", ingredient.itemData());
            }
            ingredients.add(entry);
        }

        Map<String, Object> wok = new LinkedHashMap<>();
        wok.put("total_stir_count", state.totalStirCount());
        wok.put("ingredients", ingredients);
        root.put("wok", wok);
        root.put("timestamps", Map.of(
                "last_stir_time_ms", state.lastStirTimeMs(),
                "stir_fried_time_ms", state.lastStirActionMs()
        ));
        stateStore.saveAsync(coordinates, root);
    }

    private void refreshText(StationCoordinates coordinates, WokState state) {
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

    private RecipeDocument predictRecipe(WokState state, int heatLevel) {
        if (state == null || !state.hasIngredients()) {
            return null;
        }
        List<CookingRecipeService.WokIngredientInput> actual = new ArrayList<>();
        for (WokIngredientState ingredient : state.ingredients()) {
            actual.add(new CookingRecipeService.WokIngredientInput(ingredient.source(), ingredient.amount()));
        }
        for (RecipeDocument recipe : recipeService.wokRecipes()) {
            if (recipe == null || !recipeService.canUseRecipe(recipe, null)) {
                continue;
            }
            if (recipeService.wokHeatLevel(recipe) > 0 && heatLevel > 0 && recipeService.wokHeatLevel(recipe) != heatLevel) {
                continue;
            }
            if (ingredientsMatchRecipePrefix(recipe, actual)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean ingredientsMatchRecipePrefix(RecipeDocument recipe, List<CookingRecipeService.WokIngredientInput> actual) {
        List<Map<String, Object>> expected = recipeService.wokIngredients(recipe);
        if (expected.isEmpty() || actual.size() > expected.size()) {
            return false;
        }
        for (int index = 0; index < actual.size(); index++) {
            String expectedSource = resolveIngredientSource(expected.get(index));
            if (!sourceMatches(expectedSource, actual.get(index).source())) {
                return false;
            }
        }
        return true;
    }

    private String nextStepHint(WokState state, RecipeDocument recipe) {
        List<Map<String, Object>> expected = recipeService.wokIngredients(recipe);
        boolean allIngredientsPlaced = state.ingredients().size() >= expected.size();
        if (allIngredientsPlaced) {
            boolean lastAmountComplete = true;
            int lastIndex = expected.size() - 1;
            if (lastIndex >= 0 && lastIndex < state.ingredients().size()) {
                int expectedAmount = CookingRuntimeUtil.parseInteger(expected.get(lastIndex).get("amount"), 1);
                lastAmountComplete = state.ingredients().get(lastIndex).amount() >= expectedAmount;
            }
            if (lastAmountComplete && state.totalStirCount() >= recipeService.wokStirTotalMin(recipe)) {
                return messageService.message("text_display.wok.hint_serve");
            }
            return messageService.message("text_display.wok.hint_stir");
        }
        return messageService.message("text_display.wok.hint_add");
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

    private void refreshDisplays(StationCoordinates coordinates, WokState state) {
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
            ItemSource source = ItemSourceUtil.parse(ingredient.source());
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

    private List<WokIngredientDisplay> displayIngredients(WokState state) {
        Map<String, WokIngredientDisplay> grouped = new LinkedHashMap<>();
        for (WokIngredientState ingredient : state.ingredients()) {
            String key = displayGroupKey(ingredient.source());
            ItemStack itemStack = displayItem(ingredient);
            WokIngredientDisplay existing = grouped.get(key);
            if (existing == null || !isDisplayable(existing.itemStack())) {
                grouped.put(key, new WokIngredientDisplay(ingredient.source(), itemStack));
            }
        }
        return grouped.values().stream()
                .filter(ingredient -> isDisplayable(ingredient.itemStack()))
                .toList();
    }

    private String displayGroupKey(String source) {
        ItemSource parsed = ItemSourceUtil.parse(source);
        String shorthand = parsed == null ? source : ItemSourceUtil.toShorthand(parsed);
        return Texts.normalizeId(shorthand);
    }

    private ItemStack displayItem(WokIngredientState ingredient) {
        for (Map<String, Object> itemData : ingredient.itemData()) {
            ItemStack storedItem = StoredItemCodec.deserialize(itemData);
            if (isDisplayable(storedItem)) {
                storedItem.setAmount(1);
                return storedItem;
            }
        }
        ItemSource source = ItemSourceUtil.parse(ingredient.source());
        ItemStack itemStack = source == null ? null : itemSourceService.createItem(source, 1);
        if (isDisplayable(itemStack)) {
            itemStack.setAmount(1);
        }
        return itemStack;
    }

    private boolean isDisplayable(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir();
    }

    private CookingSettingsService.Vector3 layoutOffset(int index, int count) {
        if (count <= 1) {
            return null;
        }
        double radius = settingsService.wokDisplayLayoutRadius();
        double angle = (Math.PI * 2D * index) / count;
        return new CookingSettingsService.Vector3(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
    }

    private void clearState(StationCoordinates coordinates) {
        Block block = coordinates == null ? null : coordinates.block();
        setWokHeatSourceLit(block, false);
        displayService.removeStation(StationType.WOK, coordinates);
        textDisplayService.removeStation(StationType.WOK, coordinates);
        stateStore.deleteAsync(coordinates);
    }

    private WokState readState(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (section == null || !StationType.WOK.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return null;
        }
        List<WokIngredientState> ingredients = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("wok.ingredients")) {
            Map<String, Object> normalized = emaki.jiuwu.craft.corelib.yaml.MapYamlSection.normalizeMap(entry);
            String source = String.valueOf(normalized.getOrDefault("source", ""));
            if (Texts.isBlank(source)) {
                continue;
            }
            ingredients.add(new WokIngredientState(
                    source,
                    CookingRuntimeUtil.parseInteger(normalized.get("amount"), 1),
                    CookingRuntimeUtil.parseInteger(normalized.get("stir_times"), 0),
                    readItemDataList(normalized.get("items"))
            ));
        }
        if (ingredients.isEmpty()) {
            return null;
        }
        return new WokState(
                ingredients,
                section.getInt("wok.total_stir_count", 0),
                CookingRuntimeUtil.parseLong(section.get("timestamps.last_stir_time_ms"), 0L),
                CookingRuntimeUtil.parseLong(section.get("timestamps.stir_fried_time_ms"), 0L)
        );
    }

    private boolean sourceMatches(String left, String right) {
        ItemSource leftSource = ItemSourceUtil.parse(left);
        ItemSource rightSource = ItemSourceUtil.parse(right);
        return ItemSourceUtil.matches(leftSource, rightSource);
    }

    private String resolveIngredientSource(Map<String, Object> ingredient) {
        if (ingredient == null) {
            return "";
        }
        Object itemSources = ingredient.get("item_sources");
        if (itemSources == null) {
            return "";
        }
        ItemSource source = ItemSourceUtil.parse(itemSources);
        String shorthand = source == null ? null : ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
    }

    private List<CookingRecipeService.WokIngredientInput> candidateIngredients(WokState state, String source) {
        if (Texts.isBlank(source)) {
            return List.of();
        }
        List<CookingRecipeService.WokIngredientInput> candidates = new ArrayList<>();
        if (state != null && state.hasIngredients()) {
            for (WokIngredientState ingredient : state.ingredients()) {
                candidates.add(new CookingRecipeService.WokIngredientInput(ingredient.source(), ingredient.amount()));
            }
        }
        int lastIndex = candidates.size() - 1;
        if (lastIndex >= 0 && sourceMatches(candidates.get(lastIndex).source(), source)) {
            CookingRecipeService.WokIngredientInput last = candidates.get(lastIndex);
            candidates.set(lastIndex, new CookingRecipeService.WokIngredientInput(last.source(), last.amount() + 1));
        } else {
            candidates.add(new CookingRecipeService.WokIngredientInput(source, 1));
        }
        return List.copyOf(candidates);
    }

    private List<Map<String, Object>> appendItemData(List<Map<String, Object>> current, Map<String, Object> itemData) {
        List<Map<String, Object>> updated = new ArrayList<>();
        if (current != null) {
            updated.addAll(current);
        }
        if (itemData != null && !itemData.isEmpty()) {
            updated.add(Map.copyOf(itemData));
        }
        return updated.isEmpty() ? List.of() : List.copyOf(updated);
    }

    private List<Map<String, Object>> removeLastItemData(List<Map<String, Object>> current) {
        if (current == null || current.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> updated = new ArrayList<>(current);
        updated.remove(updated.size() - 1);
        return updated.isEmpty() ? List.of() : List.copyOf(updated);
    }

    private List<Map<String, Object>> readItemDataList(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (!(plain instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> itemMap) {
                Map<String, Object> normalized = MapYamlSection.normalizeMap(itemMap);
                if (!normalized.isEmpty()) {
                    items.add(Map.copyOf(normalized));
                }
            }
        }
        return items.isEmpty() ? List.of() : List.copyOf(items);
    }


    private record WokIngredientDisplay(String source, ItemStack itemStack) {
    }


    private record WokState(List<WokIngredientState> ingredients,
            int totalStirCount,
            long lastStirTimeMs,
            long lastStirActionMs) {

        private WokState {
            ingredients = ingredients == null || ingredients.isEmpty() ? List.of() : List.copyOf(ingredients);
        }

        private boolean hasIngredients() {
            return !ingredients.isEmpty();
        }
    }

    private record WokIngredientState(String source,
            int amount,
            int stirTimes,
            List<Map<String, Object>> itemData) {

        private WokIngredientState {
            itemData = itemData == null || itemData.isEmpty()
                    ? List.of()
                    : itemData.stream()
                            .filter(entry -> entry != null && !entry.isEmpty())
                            .map(Map::copyOf)
                            .toList();
            amount = Math.max(Math.max(1, amount), itemData.size());
        }

        private ItemStack lastStoredItem() {
            if (itemData.isEmpty()) {
                return null;
            }
            return StoredItemCodec.deserialize(itemData.getLast());
        }
    }
}
