package emaki.jiuwu.craft.cooking.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
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
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class OvenRuntimeService extends TickingStationRuntimeService<OvenState> implements Listener {

    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingRecipeService recipeService;
    private final ItemSourceService itemSourceService;
    private final OvenStateCodec codec;
    private final OvenTickProcessor tickProcessor;
    private final OvenGuiController guiController;

    public OvenRuntimeService(EmakiCookingPlugin plugin,
            MessageService messageService,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            StationStateStore stateStore,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            CookingTextDisplayService textDisplayService,
            EmakiScheduling taskScheduler) {
        super(plugin, blockMatcher, stateStore, textDisplayService, taskScheduler);
        this.messageService = messageService;
        this.settingsService = settingsService;
        this.recipeService = recipeService;
        this.itemSourceService = itemSourceService;
        this.codec = new OvenStateCodec();
        this.tickProcessor = new OvenTickProcessor(settingsService, recipeService, rewardService, itemSourceService, codec);
        this.guiController = new OvenGuiController(plugin, messageService, settingsService, itemSourceService, recipeService, codec);
        this.guiController.setRuntimeService(this);
    }

    @Override
    protected StationType stationType() {
        return StationType.OVEN;
    }

    @Override
    protected OvenState createEmptyState() {
        return new OvenState();
    }

    @Override
    protected boolean stateCompletelyEmpty(OvenState state) {
        return state.isCompletelyEmpty();
    }

    @Override
    protected OvenState readState(YamlSection section) {
        return codec.readState(section);
    }

    @Override
    protected Map<String, Object> serializeState(StationCoordinates coordinates, OvenState state) {
        return codec.serializeState(coordinates, state);
    }

    @Override
    protected boolean shouldRemainActive(OvenState state, long now) {
        return tickProcessor.shouldRemainActive(state, now);
    }

    @Override
    protected boolean processStationTick(StationCoordinates coordinates, OvenState state, Block block, long now) {
        return tickProcessor.processStation(coordinates, state, block, now);
    }

    @Override
    protected void bindCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        tickProcessor.setCompletionCoordinator(completionCoordinator);
    }

    @Override
    protected void closeOpenInventories(StationCoordinates coordinates, boolean suppressSave) {
        guiController.closeOpenInventories(coordinates, suppressSave);
    }

    @Override
    protected void closeAllOpenInventories(boolean suppressSave) {
        guiController.closeAllOpenInventories(suppressSave);
    }

    @Override
    protected OvenState snapshotInventoryState(StationCoordinates coordinates,
            Inventory inventory,
            UUID playerUuid,
            String playerName) {
        return guiController.snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
    }

    @Override
    protected StationCoordinates viewingCoordinates(UUID viewerId) {
        return guiController.viewingCoordinates(viewerId);
    }

    OvenTickProcessor tickProcessor() {
        return tickProcessor;
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand()) {
            return false;
        }
        if (!blockMatcher.matches(interaction, StationType.OVEN)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, interaction.stationSource());
        if (completionCoordinator() != null && completionCoordinator().hasActive(StationType.OVEN, coordinates)) {
            interaction.cancel();
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        CookingSettingsService.OvenFuelRule fuelRule = matchFuelRule(hand);
        if (fuelRule != null && settingsService.matchesInteraction(StationType.OVEN, CookingSettingsService.INTERACTION_FUEL, interaction)) {
            interaction.cancel();
            if (!player.hasPermission(CookingPermissions.OVEN_FUEL)
                    && !player.hasPermission(CookingPermissions.ADMIN)) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return addFuel(player, coordinates, hand, fuelRule);
        }
        if ((hand == null || hand.getType().isAir())
                && settingsService.matchesInteraction(StationType.OVEN, CookingSettingsService.INTERACTION_INSPECT, interaction)) {
            interaction.cancel();
            return showInfo(player, coordinates);
        }
        if (!settingsService.matchesInteraction(StationType.OVEN, CookingSettingsService.INTERACTION_OPEN, interaction)) {
            return false;
        }
        if (!player.hasPermission(CookingPermissions.OVEN_USE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        interaction.cancel();
        return guiController.openGui(player, coordinates);
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !blockMatcher.matches(context, StationType.OVEN)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        stateStore.rememberStationSource(coordinates, context.stationSource());
        OvenGuiHolder openHolder = guiController.findOpenSession(coordinates);
        OvenState state = openHolder == null
                ? loadStateOrEmpty(coordinates)
                : guiController.snapshotInventoryState(
                        coordinates,
                        openHolder.getInventory(),
                        openHolder.viewerId(),
                        Bukkit.getPlayer(openHolder.viewerId()) == null ? "" : Bukkit.getPlayer(openHolder.viewerId()).getName()
                );
        if (state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.OVEN, coordinates);
            return false;
        }
        guiController.closeOpenInventories(coordinates, true);
        tickProcessor.dropStoredItems(block, state);
        activeStations().remove(coordinates);
        removeState(coordinates, true);
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        guiController.onInventoryClose(event);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        guiController.onInventoryClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        guiController.onInventoryDrag(event);
    }

    private boolean addFuel(Player player,
            StationCoordinates coordinates,
            ItemStack hand,
            CookingSettingsService.OvenFuelRule rule) {
        if (player == null || coordinates == null || hand == null || rule == null) {
            return false;
        }
        OvenState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long currentBurning = state.burningUntilMs();
        long durationMs = Math.max(0L, rule.durationSeconds()) * 1000L;
        long newBurning = currentBurning > now ? currentBurning + durationMs : now + durationMs;
        state.setBurningUntilMs(newBurning);
        state.setHeat(state.heat() + Math.max(0, rule.heat()));
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        CookingRuntimeUtil.takeOneFromMainHand(player);
        activeStations().add(coordinates);
        ensureTicker();
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "oven.add_fuel", Map.of(
                "item", itemDisplayName(hand),
                "seconds", Math.max(0L, (newBurning - now) / 1000L),
                "heat", state.heat()
        ));
        plugin.effectService().playActions(StationType.OVEN, "fuel", player);
        return true;
    }

    private boolean showInfo(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            return false;
        }
        OvenState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long remainingBurnTime = state.burningUntilMs() > now ? (state.burningUntilMs() - now) / 1000L : 0L;
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "oven.info", Map.of(
                "heat", state.heat(),
                "burning_time", remainingBurnTime,
                "progress", calculateProgressStatus(state)
        ));
        return true;
    }

    private String calculateProgressStatus(OvenState state) {
        if (state == null || state.slotSources().isEmpty()) {
            return messageService.message("oven.progress_not_started");
        }
        int totalRequired = 0;
        int totalProgress = 0;
        int validIngredients = 0;
        boolean allCompleted = true;
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            RecipeDocument recipe = recipeService.findOvenRecipe(entry.getValue(), null);
            if (recipe == null) {
                continue;
            }
            int required = recipeService.ovenBakeTimeSeconds(recipe);
            int progress = Math.min(required, state.progressAt(entry.getKey()));
            validIngredients++;
            totalRequired += required;
            totalProgress += progress;
            if (progress < required) {
                allCompleted = false;
            }
        }
        if (validIngredients <= 0) {
            return messageService.message("oven.progress_not_started");
        }
        if (allCompleted) {
            return messageService.message("oven.progress_completed");
        }
        if (totalRequired <= 0) {
            return "0.00%";
        }
        return String.format(Locale.ROOT, "%.2f%%", (double) totalProgress * 100.0D / (double) totalRequired);
    }

    public Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        OvenState state = loadStateOrEmpty(coordinates);
        if (state == null || state.isCompletelyEmpty()) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        Block heatBlock = block == null ? null : block.getRelative(BlockFace.DOWN);
        long now = System.currentTimeMillis();
        boolean burning = state.burningUntilMs() > now;
        long remaining = burning ? (state.burningUntilMs() - now) / 1000L : 0L;

        int totalRequired = 0;
        int totalProgress = 0;
        boolean allCompleted = !state.slotSources().isEmpty();
        String firstSource = "";
        RecipeDocument firstRecipe = null;
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            if (Texts.isBlank(firstSource)) {
                firstSource = entry.getValue();
            }
            RecipeDocument recipe = recipeService.findOvenRecipe(entry.getValue(), null);
            if (recipe == null) {
                allCompleted = false;
                continue;
            }
            if (firstRecipe == null) {
                firstRecipe = recipe;
            }
            int required = recipeService.ovenBakeTimeSeconds(recipe);
            int progress = Math.min(required, state.progressAt(entry.getKey()));
            totalRequired += required;
            totalProgress += progress;
            if (progress < required) {
                allCompleted = false;
            }
        }
        double percent = totalRequired > 0 ? Math.min(100.0D, (double) totalProgress * 100.0D / (double) totalRequired) : 0.0D;
        return Optional.of(new StationSnapshot(
                StationType.OVEN,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(plugin, block),
                CookingRuntimeUtil.resolveBlockId(plugin, heatBlock),
                burning,
                remaining,
                state.heat(),
                0,
                0,
                MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(firstSource).orElse("")),
                Texts.toStringSafe(firstSource),
                firstSource.isBlank() ? 0 : 1,
                state.slotSources().size(),
                firstRecipe == null ? "" : firstRecipe.id(),
                firstRecipe == null ? "" : firstRecipe.displayName(),
                totalProgress,
                totalRequired,
                percent,
                allCompleted && totalRequired > 0,
                "",
                0,
                state.playerName() == null ? "" : state.playerName()
        ));
    }

    @Override
    protected void refreshText(StationCoordinates coordinates, OvenState state) {
        if (!settingsService.textDisplayEnabled(StationType.OVEN) || coordinates == null
                || state == null || state.isCompletelyEmpty()) {
            textDisplayService.removeStation(StationType.OVEN, coordinates);
            return;
        }
        Location baseLocation = coordinates.location(0D, 0D, 0D);
        if (baseLocation == null || baseLocation.getWorld() == null) {
            textDisplayService.removeStation(StationType.OVEN, coordinates);
            return;
        }
        long now = System.currentTimeMillis();
        long remainingBurn = state.burningUntilMs() > now ? (state.burningUntilMs() - now) / 1000L : 0L;
        StringBuilder builder = new StringBuilder();
        appendLine(builder, messageService.message("text_display.oven.title"));
        appendLine(builder, messageService.message("text_display.oven.heat", Map.of("heat", state.heat())));
        appendLine(builder, messageService.message("text_display.oven.burning", Map.of("burning_time", remainingBurn)));
        if (state.hasSlots()) {
            appendLine(builder, messageService.message("text_display.oven.progress", Map.of("progress", calculateProgressStatus(state))));
        } else {
            appendLine(builder, messageService.message("text_display.oven.idle"));
        }
        textDisplayService.upsert(new CookingTextDisplaySpec(
                StationType.OVEN,
                coordinates,
                "info",
                builder.toString(),
                baseLocation,
                settingsService.textDisplayProfile(StationType.OVEN)
        ));
    }

    private CookingSettingsService.OvenFuelRule matchFuelRule(ItemStack itemStack) {
        ItemSourceRef identified = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        if (identified == null) {
            return null;
        }
        for (CookingSettingsService.OvenFuelRule rule : settingsService.ovenFuels()) {
            if (rule != null && ItemSourceUtil.matches(rule.source(), identified)
                    && CookingMatchers.test(rule.matcher(), itemStack, identified, null)) {
                return rule;
            }
        }
        return null;
    }
}
