package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplaySpec;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplaySpec;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
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

public final class ChoppingBoardRuntimeService {

    private static final double DISPLAY_SEARCH_RADIUS = 1.5D;

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

    public ChoppingBoardRuntimeService(EmakiCookingPlugin plugin,
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
        displayService.removeStationType(StationType.CHOPPING_BOARD);
        textDisplayService.removeStationType(StationType.CHOPPING_BOARD);
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.CHOPPING_BOARD).entrySet()) {
            StationCoordinates coordinates = entry.getKey();
            ChoppingBoardState state = readState(entry.getValue());
            ItemSource stationSource = stateStore.stationSource(entry.getValue());
            Block block = coordinates.block();
            if (state == null || !blockMatcher.matches(block, StationType.CHOPPING_BOARD, stationSource)) {
                clearDisplay(coordinates, state == null ? null : state.displayEntityId(), state == null ? null : state.inputSource());
                stateStore.deleteAsync(coordinates);
                continue;
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
                            state.cutCount(),
                            state.lastInteractionMs(),
                            null
                    ));
                }
            }
        }
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
        ChoppingBoardState state = readState(stateStore.load(coordinates));
        long now = System.currentTimeMillis();

        if (settingsService.matchesInteraction(
                StationType.CHOPPING_BOARD,
                CookingSettingsService.INTERACTION_RETURN_INPUT,
                interaction)) {
            if (state == null || !state.hasInputSource()) {
                return false;
            }
            returnStoredInput(player, coordinates, state);
            interaction.cancel();
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (state != null && state.hasInputSource()) {
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
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.too_fast", Map.of());
                interaction.cancel();
                return true;
            }
            if (hand == null || hand.getType().isAir()) {
                return false;
            }
            if (!isTool(hand)) {
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
            RecipeDocument recipe = recipeService.findChoppingBoardRecipe(state.inputSource(), player);
            if (recipe == null) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.cannot_cut", Map.of());
                interaction.cancel();
                return true;
            }
            int cutsRequired = recipeService.choppingCutsRequired(recipe);
            if (cutsRequired <= 0) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.cannot_cut", Map.of());
                interaction.cancel();
                return true;
            }

            int nextCutCount = state.cutCount() + 1;
            applyToolDamage(player, hand, recipeService.choppingToolDamage(recipe));
            maybeDamagePlayer(player, recipeService.choppingDamageChance(recipe), recipeService.choppingDamageValue(recipe));
            plugin.effectService().playActions(StationType.CHOPPING_BOARD, "cut", player);

            if (nextCutCount >= cutsRequired) {
                clearDisplay(coordinates, state.displayEntityId(), state.inputSource());
                textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
                stateStore.deleteAsync(coordinates);
                rewardService.deliver(
                        recipe,
                        player,
                        block.getLocation().add(0.5D, 1.0D, 0.5D),
                        settingsService.choppingDropResult(),
                        recipeService.outputs(recipe),
                        recipeService.actions(recipe),
                        "cooking_chopping_board_complete",
                        Map.of(
                                "recipe_id", recipe.id(),
                                "station_type", StationType.CHOPPING_BOARD.folderName()
                        )
                );
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.completed", Map.of("recipe", recipe.displayName()));
                plugin.effectService().playActions(StationType.CHOPPING_BOARD, "complete", player);
                interaction.cancel();
                return true;
            }

            ChoppingBoardState updated = new ChoppingBoardState(
                    state.inputSource(),
                    state.inputItemData(),
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
        ItemSource source = itemSourceService.identifyItem(hand);
        String shorthand = source == null ? null : ItemSourceUtil.toShorthand(source);
        if (shorthand == null || shorthand.isBlank()) {
            return false;
        }
        if (settingsService.onlyRecipeItems(StationType.CHOPPING_BOARD)
                && recipeService.findChoppingBoardRecipe(shorthand, player) == null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "general.input_rejected", Map.of());
            interaction.cancel();
            return true;
        }
        ItemStack displayItem = CookingRuntimeUtil.takeOneFromMainHand(player);
        if (displayItem == null || displayItem.getType().isAir()) {
            return false;
        }
        Map<String, Object> itemData = StoredItemCodec.serialize(displayItem);
        refreshDisplay(coordinates, shorthand, itemData);
        ChoppingBoardState updated = new ChoppingBoardState(shorthand, itemData, 0, now, null);
        saveState(coordinates, updated);
        refreshText(coordinates, updated);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.item_placed", Map.of());
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
        ChoppingBoardState state = readState(stateStore.load(coordinates));
        if (state == null) {
            return false;
        }
        if (state.hasInputSource()) {
            ItemStack itemStack = storedItemOrFallback(state.inputSource(), state.inputItemData(), 1);
            if (itemStack != null && !itemStack.getType().isAir()) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 1.0D, 0.5D), itemStack);
            }
        }
        clearDisplay(coordinates, state.displayEntityId(), state.inputSource());
        textDisplayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
        stateStore.deleteAsync(coordinates);
        return true;
    }

    private boolean isTool(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemSource source = itemSourceService.identifyItem(itemStack);
        if (source == null) {
            return false;
        }
        for (ItemSource tool : settingsService.choppingToolSources()) {
            if (ItemSourceUtil.matches(tool, source)) {
                return true;
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
        ItemStack itemStack = storedItemOrFallback(state.inputSource(), state.inputItemData(), 1);
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.getInventory().setItemInMainHand(itemStack);
        } else {
            InventoryItemUtil.giveOrDrop(player, itemStack);
        }
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.item_returned", Map.of());
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
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.CHOPPING_BOARD, coordinates);
        if (state.hasInputSource()) {
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("source", state.inputSource());
            if (state.inputItemData() != null && !state.inputItemData().isEmpty()) {
                inputItem.put("item", state.inputItemData());
            }
            root.put("input_item", inputItem);
        }
        root.put("timestamps", Map.of("last_interaction_ms", state.lastInteractionMs()));
        root.put("chopping_board", Map.of("cut_count", state.cutCount()));
        stateStore.saveAsync(coordinates, root);
    }

    private ChoppingBoardState readState(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (section == null || !StationType.CHOPPING_BOARD.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return null;
        }
        String inputSource = section.getString("input_item.source", "");
        Map<String, Object> inputItemData = readItemData(section.get("input_item.item"));
        Integer cutCount = section.getInt("chopping_board.cut_count", 0);
        UUID displayId = CookingRuntimeUtil.parseUuid(section.getString("display_entity.uuid", ""));
        long lastInteraction = CookingRuntimeUtil.parseLong(section.get("timestamps.last_interaction_ms"), 0L);
        return new ChoppingBoardState(inputSource, inputItemData, cutCount == null ? 0 : cutCount, lastInteraction, displayId);
    }

    private void refreshDisplay(StationCoordinates coordinates, String inputSource, Map<String, Object> inputItemData) {
        ItemSource source = ItemSourceUtil.parse(inputSource);
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
        ItemSource source = ItemSourceUtil.parse(sourceText);
        return source == null ? null : itemSourceService.createItem(source, amount);
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
        ItemSource source = ItemSourceUtil.parse(state.inputSource());
        String itemName = source == null ? state.inputSource() : itemSourceService.displayName(source);
        if (Texts.isBlank(itemName)) {
            itemName = state.inputSource();
        }
        appendLine(builder, messageService.message("text_display.chopping_board.placed", Map.of("item", itemName)));
        RecipeDocument recipe = recipeService.findChoppingBoardRecipe(state.inputSource(), null);
        int cutsRequired = recipe == null ? 0 : recipeService.choppingCutsRequired(recipe);
        if (recipe == null || cutsRequired <= 0) {
            appendLine(builder, messageService.message("text_display.chopping_board.no_recipe"));
        } else {
            appendLine(builder, messageService.message("text_display.chopping_board.progress", Map.of(
                    "current", state.cutCount(),
                    "required", cutsRequired
            )));
            appendLine(builder, messageService.message("text_display.chopping_board.hint_cut"));
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
        ItemSource source = ItemSourceUtil.parse(inputSource);
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
            int cutCount,
            long lastInteractionMs,
            UUID displayEntityId) {

        private ChoppingBoardState {
            inputItemData = inputItemData == null || inputItemData.isEmpty() ? Map.of() : Map.copyOf(inputItemData);
        }

        private boolean hasInputSource() {
            return inputSource != null && !inputSource.isBlank();
        }
    }
}
