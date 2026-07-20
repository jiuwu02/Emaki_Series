package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GrinderRuntimeService {

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final CookingTextDisplayService textDisplayService;
    private final ExecutionDispatcher executionDispatcher;
    private final Set<String> activeStations = ConcurrentHashMap.newKeySet();
    private final Set<String> tickingStations = ConcurrentHashMap.newKeySet();
    private CookingCompletionCoordinator completionCoordinator;
    private TaskHandle tickerTask;

    public GrinderRuntimeService(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            CookingTextDisplayService textDisplayService,
            ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.stateStore = stateStore;
        this.recipeService = recipeService;
        this.rewardService = rewardService;
        this.itemSourceService = itemSourceService;
        this.textDisplayService = textDisplayService;
        this.executionDispatcher = executionDispatcher;
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
                return StationType.GRINDER;
            }

            @Override
            public Map<String, Object> snapshot(StationCoordinates coordinates) {
                GrinderState state = readState(stateStore.load(coordinates));
                return state == null ? null : serializeState(coordinates, state);
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> replace(
                    StationCoordinates coordinates,
                    Map<String, Object> committedState) {
                GrinderState state = readState(new emaki.jiuwu.craft.corelib.yaml.MapYamlSection(committedState));
                if (state == null) {
                    return java.util.concurrent.CompletableFuture.failedFuture(
                            new IllegalArgumentException("Invalid committed grinder state"));
                }
                return stateStore.saveAsync(coordinates, committedState)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            activeStations.add(coordinates.runtimeKey());
                            refreshText(coordinates, state);
                        }));
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> delete(StationCoordinates coordinates) {
                return stateStore.deleteAsync(coordinates)
                        .thenCompose(CookingCompletionStateAccesses::requireSaved)
                        .thenCompose(_ -> CookingCompletionStateAccesses.runAtStation(plugin, coordinates, () -> {
                            activeStations.remove(coordinates.runtimeKey());
                            textDisplayService.removeStation(StationType.GRINDER, coordinates);
                        }));
            }
        };
    }

    public void reload() {
        cancelTicker();
        activeStations.clear();
        textDisplayService.removeStationType(StationType.GRINDER);
        stateStore.forEachLoadedState(StationType.GRINDER, this::restoreStoredState);
        ensureTicker();
    }

    public boolean restoreStoredState(StationCoordinates coordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        GrinderState state = readState(section);
        ItemSource stationSource = stateStore.stationSource(section);
        Block block = coordinates.block();
        if (state == null) {
            activeStations.remove(coordinates.runtimeKey());
            textDisplayService.removeStation(StationType.GRINDER, coordinates);
            return false;
        }
        if (!blockMatcher.matches(block, StationType.GRINDER, stationSource)) {
            activeStations.remove(coordinates.runtimeKey());
            textDisplayService.removeStation(StationType.GRINDER, coordinates);
            plugin.getLogger().warning("Station restore report: skipped_mismatch type=grinder coordinate=" + coordinates.runtimeKey());
            return false;
        }
        activeStations.add(coordinates.runtimeKey());
        refreshText(coordinates, state);
        ensureTicker();
        return true;
    }

    public void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        activeStations.remove(coordinates.runtimeKey());
        textDisplayService.removeStation(StationType.GRINDER, coordinates);
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    public void shutdown() {
        cancelTicker();
        waitForInFlightTicks();
        activeStations.clear();
        textDisplayService.removeStationType(StationType.GRINDER);
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand()
                || !blockMatcher.matches(interaction, StationType.GRINDER)) {
            return false;
        }
        if (!settingsService.matchesInteraction(
                StationType.GRINDER,
                CookingSettingsService.INTERACTION_START,
                interaction)) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.GRINDER_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.GRINDER, coordinates)) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "grinder.busy", Map.of());
            interaction.cancel();
            return true;
        }
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
        refreshText(coordinates, state);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "grinder.started", Map.of("seconds", recipeService.grinderTimeSeconds(recipe)));
        plugin.effectService().playActions(StationType.GRINDER, "start", player);
        interaction.cancel();
        return true;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(context, StationType.GRINDER)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, context.stationSource());
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.GRINDER, coordinates)) {
            return true;
        }
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
        stateStore.deleteAsync(coordinates);
        textDisplayService.removeStation(StationType.GRINDER, coordinates);
        return true;
    }




    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        GrinderState state = readState(stateStore.load(coordinates));
        if (state == null) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        RecipeDocument recipe = recipeService.grinderRecipeById(state.recipeId());
        int target = recipe == null ? 0 : recipeService.grinderTimeSeconds(recipe);
        int elapsedSeconds = (int) Math.max(0L, (System.currentTimeMillis() - state.startTimeMs()) / 1000L);
        int current = target > 0 ? Math.min(elapsedSeconds, target) : elapsedSeconds;
        double percent = target > 0 ? Math.min(100.0D, (double) current * 100.0D / (double) target) : 0.0D;
        boolean completed = target > 0 && elapsedSeconds >= target;
        return Optional.of(new StationSnapshot(
                StationType.GRINDER,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(plugin, block),
                "",
                false,
                0L,
                0, 0, 0,
                MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(state.inputSource())),
                state.inputSource(),
                state.hasInputSource() ? 1 : 0,
                state.hasInputSource() ? 1 : 0,
                recipe == null ? "" : recipe.id(),
                recipe == null ? "" : recipe.displayName(),
                current,
                target,
                percent,
                completed,
                "",
                0,
                state.playerName() == null ? "" : state.playerName()
        ));
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
        tickerTask = executionDispatcher.runGlobalTimer(plugin, this::tick, interval, interval);
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

    private void tick() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        for (String stationKey : List.copyOf(activeStations)) {
            StationCoordinates coordinates = parseRuntimeKey(stationKey);
            if (coordinates == null || !tickingStations.add(stationKey)) {
                continue;
            }
            Location location = coordinates.location(0.5D, 0.5D, 0.5D);
            if (location == null || location.getWorld() == null) {
                tickingStations.remove(stationKey);
                activeStations.remove(stationKey);
                continue;
            }
            TaskHandle handle = executionDispatcher.runAtLocation(plugin, location, () -> {
                try {
                    GrinderState state = readState(stateStore.load(coordinates));
                    if (state == null) {
                        activeStations.remove(stationKey);
                        return;
                    }
                    processStation(coordinates, state);
                } finally {
                    tickingStations.remove(stationKey);
                }
            });
            if (handle == null) {
                tickingStations.remove(stationKey);
            }
        }
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    private void processStation(StationCoordinates coordinates, GrinderState state) {
        if (completionCoordinator != null && completionCoordinator.hasActive(StationType.GRINDER, coordinates)) {
            return;
        }
        Block block = coordinates.block();
        RecipeDocument recipe = recipeService.grinderRecipeById(state.recipeId());
        ItemSource stationSource = stateStore.rememberedStationSource(coordinates);
        if (block == null || recipe == null || !blockMatcher.matches(block, StationType.GRINDER, stationSource)) {
            activeStations.remove(coordinates.runtimeKey());
            stateStore.deleteAsync(coordinates);
            textDisplayService.removeStation(StationType.GRINDER, coordinates);
            return;
        }
        int grindTimeSeconds = recipeService.grinderTimeSeconds(recipe);
        long elapsed = System.currentTimeMillis() - state.startTimeMs();
        if (elapsed >= grindTimeSeconds * 1000L) {
            complete(coordinates, block, state, recipe);
            return;
        }
        refreshText(coordinates, state);
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(Particle.CLOUD, location, 3, 0.15D, 0.15D, 0.15D, 0.01D);
        }
    }

    private void complete(StationCoordinates coordinates, Block block, GrinderState state, RecipeDocument recipe) {
        Player player = state.playerUuid() == null ? null : Bukkit.getPlayer(state.playerUuid());
        Location rewardLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
        boolean accepted = completionCoordinator != null && completionCoordinator.submit(new CookingCompletionRequest(
                "grind:" + state.startTimeMs(),
                StationType.GRINDER,
                coordinates,
                serializeState(coordinates, state),
                CookingCompletionOperation.CommitMode.DELETE,
                Map.of(),
                recipe,
                player,
                rewardLocation,
                settingsService.grinderDropResult(),
                List.of(new CookingInputIngredient(state.inputSource(), 1)),
                recipeService.outputs(recipe),
                recipeService.actions(recipe),
                "cooking_grinder_complete",
                Map.of(
                        "recipe_id", recipe.id(),
                        "station_type", StationType.GRINDER.folderName()
                ),
                List.of()
        ));
        if (accepted && player != null && player.isOnline()) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "grinder.completed", Map.of("recipe", recipe.displayName()));
            plugin.effectService().playActions(StationType.GRINDER, "complete", player);
        }
    }

    private void refreshText(StationCoordinates coordinates, GrinderState state) {
        if (!settingsService.textDisplayEnabled(StationType.GRINDER) || coordinates == null || state == null) {
            textDisplayService.removeStation(StationType.GRINDER, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.GRINDER, coordinates);
            return;
        }
        RecipeDocument recipe = recipeService.grinderRecipeById(state.recipeId());
        int grindTimeSeconds = recipe == null ? 0 : recipeService.grinderTimeSeconds(recipe);
        long elapsedMs = System.currentTimeMillis() - state.startTimeMs();
        long remainingSeconds = Math.max(0L, (grindTimeSeconds * 1000L - elapsedMs + 999L) / 1000L);

        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.grinder.title"));
        if (state.hasInputSource()) {
            String itemName = EmakiCoreLibApi.itemDisplayName(state.inputSource());
            if (itemName == null || itemName.isBlank()) {
                itemName = state.inputSource();
            }
            appendLine(builder, messageService.message("text_display.grinder.item", Map.of("item", itemName)));
        }
        appendLine(builder, messageService.message("text_display.grinder.grinding", Map.of("seconds", remainingSeconds)));
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.GRINDER,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.GRINDER)
        ));
    }

    private StationCoordinates parseRuntimeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String[] parts = key.split(":", -1);
        if (parts.length < 4) {
            return null;
        }
        try {
            int z = Integer.parseInt(parts[parts.length - 1]);
            int y = Integer.parseInt(parts[parts.length - 2]);
            int x = Integer.parseInt(parts[parts.length - 3]);
            String world = String.join(":", java.util.Arrays.copyOf(parts, parts.length - 3));
            return new StationCoordinates(world, x, y, z);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void appendLine(StringBuilder builder, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private void saveState(StationCoordinates coordinates, GrinderState state) {
        stateStore.saveAsync(coordinates, serializeState(coordinates, state));
    }

    private Map<String, Object> serializeState(StationCoordinates coordinates, GrinderState state) {
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
        return root;
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
