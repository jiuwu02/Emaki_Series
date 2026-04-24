package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class GrinderRuntimeService {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final Set<String> activeStations = ConcurrentHashMap.newKeySet();
    private BukkitTask tickerTask;

    public GrinderRuntimeService(EmakiCookingPlugin plugin,
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
        cancelTicker();
        activeStations.clear();
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.GRINDER).entrySet()) {
            StationCoordinates coordinates = entry.getKey();
            GrinderState state = readState(entry.getValue());
            Block block = coordinates.block();
            if (state == null || block == null || !blockMatcher.matches(block, StationType.GRINDER)) {
                stateStore.delete(coordinates);
                continue;
            }
            activeStations.add(coordinates.runtimeKey());
        }
        ensureTicker();
    }

    public void shutdown() {
        cancelTicker();
        activeStations.clear();
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand()
                || !interaction.leftClick()
                || !blockMatcher.matches(block, StationType.GRINDER)) {
            return false;
        }
        if (settingsService.requireSneaking(StationType.GRINDER) && !player.isSneaking()) {
            return false;
        }
        if (!player.hasPermission("emakicooking.station.grinder.use")
                && !player.hasPermission("emakicooking.admin")) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        GrinderState existing = readState(stateStore.load(coordinates));
        if (existing != null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "grinder.busy", Map.of());
            interaction.cancel();
            return true;
        }
        ItemSource source = itemSourceService.identifyItem(hand);
        String shorthand = source == null ? null : ItemSourceUtil.toShorthand(source);
        RecipeDocument recipe = recipeService.findGrinderRecipe(shorthand, player);
        if (recipe == null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "grinder.no_recipe", Map.of());
            interaction.cancel();
            return true;
        }
        ItemStack consumed = CookingRuntimeUtil.takeOneFromMainHand(player);
        if (consumed == null || consumed.getType().isAir()) {
            return false;
        }
        GrinderState state = new GrinderState(
                shorthand,
                recipe.id(),
                System.currentTimeMillis(),
                player.getUniqueId(),
                player.getName()
        );
        saveState(coordinates, state);
        activeStations.add(coordinates.runtimeKey());
        ensureTicker();
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "grinder.started", Map.of("seconds", recipeService.grinderTimeSeconds(recipe)));
        interaction.cancel();
        return true;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(block, StationType.GRINDER)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        GrinderState state = readState(stateStore.load(coordinates));
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
        activeStations.remove(coordinates.runtimeKey());
        stateStore.delete(coordinates);
        return true;
    }

    private void ensureTicker() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        int interval = settingsService.grinderCheckDelayTicks();
        if (tickerTask != null && !tickerTask.isCancelled()) {
            return;
        }
        tickerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void tick() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.GRINDER).entrySet()) {
            GrinderState state = readState(entry.getValue());
            StationCoordinates coordinates = entry.getKey();
            if (state == null) {
                activeStations.remove(coordinates.runtimeKey());
                continue;
            }
            processStation(coordinates, state);
        }
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    private void processStation(StationCoordinates coordinates, GrinderState state) {
        Block block = coordinates.block();
        RecipeDocument recipe = recipeService.grinderRecipeById(state.recipeId());
        if (block == null || recipe == null || !blockMatcher.matches(block, StationType.GRINDER)) {
            activeStations.remove(coordinates.runtimeKey());
            stateStore.delete(coordinates);
            return;
        }
        int grindTimeSeconds = recipeService.grinderTimeSeconds(recipe);
        long elapsed = System.currentTimeMillis() - state.startTimeMs();
        if (elapsed >= grindTimeSeconds * 1000L) {
            complete(coordinates, block, state, recipe);
            return;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(Particle.CLOUD, location, 3, 0.15D, 0.15D, 0.15D, 0.01D);
        }
    }

    private void complete(StationCoordinates coordinates, Block block, GrinderState state, RecipeDocument recipe) {
        Player player = state.playerUuid() == null ? null : Bukkit.getPlayer(state.playerUuid());
        Location rewardLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
        rewardService.deliver(
                recipe,
                player,
                rewardLocation,
                settingsService.grinderDropResult(),
                recipeService.outputs(recipe),
                recipeService.actions(recipe),
                "cooking_grinder_complete",
                Map.of(
                        "recipe_id", recipe.id(),
                        "station_type", StationType.GRINDER.folderName()
                )
        );
        if (player != null && player.isOnline()) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "grinder.completed", Map.of("recipe", recipe.displayName()));
        }
        activeStations.remove(coordinates.runtimeKey());
        stateStore.delete(coordinates);
    }

    private void saveState(StationCoordinates coordinates, GrinderState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.GRINDER, coordinates);
        if (state.hasInputSource()) {
            root.put("input_item", Map.of("source", state.inputSource()));
        }
        Map<String, Object> grinder = new LinkedHashMap<>();
        grinder.put("recipe_id", state.recipeId());
        grinder.put("start_time_ms", state.startTimeMs());
        if (state.playerUuid() != null) {
            grinder.put("player_uuid", state.playerUuid().toString());
        }
        if (state.playerName() != null && !state.playerName().isBlank()) {
            grinder.put("player_name", state.playerName());
        }
        root.put("grinder", grinder);
        stateStore.save(coordinates, root);
    }

    private GrinderState readState(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (section == null || !StationType.GRINDER.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return null;
        }
        String inputSource = section.getString("input_item.source", "");
        String recipeId = section.getString("grinder.recipe_id", "");
        long startTime = CookingRuntimeUtil.parseLong(section.get("grinder.start_time_ms"), 0L);
        UUID playerUuid = CookingRuntimeUtil.parseUuid(section.getString("grinder.player_uuid", ""));
        String playerName = section.getString("grinder.player_name", "");
        return new GrinderState(inputSource, recipeId, startTime, playerUuid, playerName);
    }

    private record GrinderState(String inputSource, String recipeId, long startTimeMs, UUID playerUuid, String playerName) {

        private boolean hasInputSource() {
            return inputSource != null && !inputSource.isBlank();
        }
    }
}
