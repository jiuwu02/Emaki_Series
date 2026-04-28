package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
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
    private final Map<String, UUID> displayEntities = new LinkedHashMap<>();

    public ChoppingBoardRuntimeService(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.recipeService = recipeService;
        this.rewardService = rewardService;
        this.itemSourceService = itemSourceService;
    }

    public void reload() {
        displayEntities.clear();
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.CHOPPING_BOARD).entrySet()) {
            StationCoordinates coordinates = entry.getKey();
            ChoppingBoardState state = readState(entry.getValue());
            Block block = coordinates.block();
            if (state == null || block == null || !blockMatcher.matches(block, StationType.CHOPPING_BOARD)) {
                clearDisplay(coordinates, state == null ? null : state.displayEntityId(), state == null ? null : state.inputSource());
                stateStore.delete(coordinates);
                continue;
            }
            if (state.hasInputSource()) {
                UUID displayId = spawnDisplay(coordinates, state.inputSource(), state.displayEntityId());
                if (displayId != null && !displayId.equals(state.displayEntityId())) {
                    saveState(coordinates, new ChoppingBoardState(
                            state.inputSource(),
                            state.cutCount(),
                            state.lastInteractionMs(),
                            displayId
                    ));
                }
            }
        }
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        if (block == null || !interaction.mainHand() || !blockMatcher.matches(block, StationType.CHOPPING_BOARD)) {
            return false;
        }
        Player player = interaction.player();
        if (player == null) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        ChoppingBoardState state = readState(stateStore.load(coordinates));
        long now = System.currentTimeMillis();

        if (interaction.rightClick()) {
            if (state == null || !state.hasInputSource()) {
                return false;
            }
            returnStoredInput(player, coordinates, state);
            interaction.cancel();
            return true;
        }

        if (!interaction.leftClick()) {
            return false;
        }
        if (settingsService.requireSneaking(StationType.CHOPPING_BOARD) && !player.isSneaking()) {
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
        if (state != null && settingsService.choppingInteractionDelayMs() > 0L
                && now - state.lastInteractionMs() < settingsService.choppingInteractionDelayMs()) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.too_fast", Map.of());
            interaction.cancel();
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (state != null && state.hasInputSource()) {
            if (hand == null || hand.getType().isAir()) {
                returnStoredInput(player, coordinates, state);
                interaction.cancel();
                return true;
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

            if (nextCutCount >= cutsRequired) {
                clearDisplay(coordinates, state.displayEntityId(), state.inputSource());
                stateStore.delete(coordinates);
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
                interaction.cancel();
                return true;
            }

            ChoppingBoardState updated = new ChoppingBoardState(
                    state.inputSource(),
                    nextCutCount,
                    now,
                    state.displayEntityId()
            );
            saveState(coordinates, updated);
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.progress", Map.of(
                    "current", nextCutCount,
                    "required", cutsRequired
            ));
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
        ItemStack displayItem = CookingRuntimeUtil.takeOneFromMainHand(player);
        if (displayItem == null || displayItem.getType().isAir()) {
            return false;
        }
        UUID displayId = spawnDisplay(coordinates, shorthand, state == null ? null : state.displayEntityId());
        ChoppingBoardState updated = new ChoppingBoardState(shorthand, 0, now, displayId);
        saveState(coordinates, updated);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "chopping_board.item_placed", Map.of());
        interaction.cancel();
        return true;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(block, StationType.CHOPPING_BOARD)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        ChoppingBoardState state = readState(stateStore.load(coordinates));
        if (state == null) {
            return false;
        }
        if (state.hasInputSource()) {
            ItemSource source = ItemSourceUtil.parse(state.inputSource());
            ItemStack itemStack = itemSourceService.createItem(source, 1);
            if (itemStack != null && !itemStack.getType().isAir()) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 1.0D, 0.5D), itemStack);
            }
        }
        clearDisplay(coordinates, state.displayEntityId(), state.inputSource());
        stateStore.delete(coordinates);
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
        stateStore.delete(coordinates);
        if (state == null || !state.hasInputSource()) {
            return;
        }
        ItemSource source = ItemSourceUtil.parse(state.inputSource());
        ItemStack itemStack = itemSourceService.createItem(source, 1);
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
            root.put("input_item", Map.of("source", state.inputSource()));
        }
        root.put("display_entity", state.displayEntityId() == null ? Map.of() : Map.of("uuid", state.displayEntityId().toString()));
        root.put("timestamps", Map.of("last_interaction_ms", state.lastInteractionMs()));
        root.put("chopping_board", Map.of("cut_count", state.cutCount()));
        stateStore.save(coordinates, root);
    }

    private ChoppingBoardState readState(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (section == null || !StationType.CHOPPING_BOARD.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return null;
        }
        String inputSource = section.getString("input_item.source", "");
        Integer cutCount = section.getInt("chopping_board.cut_count", 0);
        UUID displayId = CookingRuntimeUtil.parseUuid(section.getString("display_entity.uuid", ""));
        long lastInteraction = CookingRuntimeUtil.parseLong(section.get("timestamps.last_interaction_ms"), 0L);
        return new ChoppingBoardState(inputSource, cutCount == null ? 0 : cutCount, lastInteraction, displayId);
    }

    private UUID spawnDisplay(StationCoordinates coordinates, String inputSource, UUID knownId) {
        clearDisplay(coordinates, knownId, inputSource);
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
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        Location location = adjustment.applyOffset(baseLocation);
        if (location == null || location.getWorld() == null) {
            return null;
        }
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(itemStack);
            entity.setTransformation(adjustment.transformation());
            entity.setInvulnerable(true);
            entity.setPersistent(true);
            entity.setSilent(true);
            entity.setGravity(false);
        });
        displayEntities.put(coordinates.runtimeKey(), display.getUniqueId());
        return display.getUniqueId();
    }

    private void clearDisplay(StationCoordinates coordinates, UUID knownId, String inputSource) {
        if (coordinates == null) {
            return;
        }
        UUID tracked = knownId == null ? displayEntities.remove(coordinates.runtimeKey()) : knownId;
        Location baseLocation = coordinates.location(0.5D, 0.5D, 0.5D);
        Location targetLocation = resolveDisplayLocation(coordinates, inputSource);
        Location searchLocation = targetLocation == null ? baseLocation : targetLocation;
        if (tracked != null && searchLocation != null && searchLocation.getWorld() != null) {
            Entity trackedEntity = Bukkit.getEntity(tracked);
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

    private record ChoppingBoardState(String inputSource, int cutCount, long lastInteractionMs, UUID displayEntityId) {

        private boolean hasInputSource() {
            return inputSource != null && !inputSource.isBlank();
        }
    }
}
