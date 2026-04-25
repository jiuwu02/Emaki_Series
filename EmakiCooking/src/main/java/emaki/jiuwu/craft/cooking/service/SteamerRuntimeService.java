package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class SteamerRuntimeService implements Listener {

    private static final long DIRTY_FLUSH_INTERVAL_TICKS = 100L;

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final StationStateStore stateStore;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final Map<UUID, SteamerGuiHolder> openSessions = new ConcurrentHashMap<>();
    private final Map<StationCoordinates, SteamerState> runtimeStates = new ConcurrentHashMap<>();
    private final Set<StationCoordinates> activeStations = ConcurrentHashMap.newKeySet();
    private final Set<StationCoordinates> dirtyStations = ConcurrentHashMap.newKeySet();
    private BukkitTask tickerTask;
    private BukkitTask flushTask;

    public SteamerRuntimeService(EmakiCookingPlugin plugin,
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
        closeAllOpenInventories(false);
        flushDirtyStates();
        cancelFlushTask();
        cancelTicker();
        activeStations.clear();
        runtimeStates.clear();
        dirtyStations.clear();
        long now = System.currentTimeMillis();
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.STEAMER).entrySet()) {
            StationCoordinates coordinates = entry.getKey();
            SteamerState state = readState(entry.getValue());
            Block block = coordinates.block();
            if (state == null || block == null || !blockMatcher.matches(block, StationType.STEAMER)) {
                closeOpenInventories(coordinates, true);
                removeState(coordinates, true);
                continue;
            }
            cacheState(coordinates, state);
            if (shouldRemainActive(state, now)) {
                activeStations.add(coordinates);
            }
        }
        ensureTicker();
    }

    public void shutdown() {
        closeAllOpenInventories(false);
        flushDirtyStates();
        cancelFlushTask();
        cancelTicker();
        activeStations.clear();
        runtimeStates.clear();
        dirtyStations.clear();
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.rightClick() || !interaction.mainHand()) {
            return false;
        }
        if (blockMatcher.matches(block, StationType.STEAMER)) {
            if (settingsService.requireSneaking(StationType.STEAMER) && !player.isSneaking()) {
                return false;
            }
            if (!player.hasPermission("emakicooking.station.steamer.use")
                    && !player.hasPermission("emakicooking.admin")) {
                messageService.send(player, "general.no_permission");
                interaction.cancel();
                return true;
            }
            if (!isHeatSourceBlock(block.getRelative(BlockFace.DOWN))) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.no_heat_source", Map.of());
                interaction.cancel();
                return true;
            }
            interaction.cancel();
            return openGui(player, StationCoordinates.fromBlock(block));
        }

        if (!isHeatSourceBlock(block)) {
            return false;
        }
        Block topBlock = block.getRelative(BlockFace.UP);
        if (!blockMatcher.matches(topBlock, StationType.STEAMER)) {
            return false;
        }
        if (settingsService.requireSneaking(StationType.STEAMER) && !player.isSneaking()) {
            return false;
        }
        interaction.cancel();
        ItemStack hand = player.getInventory().getItemInMainHand();
        StationCoordinates coordinates = StationCoordinates.fromBlock(topBlock);
        if (hand == null || hand.getType().isAir()) {
            return showInfo(player, coordinates);
        }

        CookingSettingsService.SteamerMoistureRule moistureRule = matchMoistureRule(hand);
        if (moistureRule != null) {
            if (!player.hasPermission("emakicooking.station.steamer.moisture")
                    && !player.hasPermission("emakicooking.admin")) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return addMoisture(player, coordinates, hand, moistureRule);
        }

        CookingSettingsService.SteamerFuelRule fuelRule = matchFuelRule(hand);
        if (fuelRule != null) {
            if (!player.hasPermission("emakicooking.station.steamer.fuel")
                    && !player.hasPermission("emakicooking.admin")) {
                messageService.send(player, "general.no_permission");
                return true;
            }
            return addFuel(player, coordinates, block, hand, fuelRule);
        }
        return true;
    }

    public boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null) {
            return false;
        }
        Block steamerBlock = null;
        if (blockMatcher.matches(block, StationType.STEAMER)) {
            steamerBlock = block;
        } else if (isHeatSourceBlock(block) && blockMatcher.matches(block.getRelative(BlockFace.UP), StationType.STEAMER)) {
            steamerBlock = block.getRelative(BlockFace.UP);
        }
        if (steamerBlock == null) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(steamerBlock);
        SteamerGuiHolder openHolder = findOpenSession(coordinates);
        SteamerState state = openHolder == null
                ? loadStateOrEmpty(coordinates)
                : snapshotInventoryState(
                        coordinates,
                        openHolder.getInventory(),
                        openHolder.viewerId(),
                        Bukkit.getPlayer(openHolder.viewerId()) == null ? "" : Bukkit.getPlayer(openHolder.viewerId()).getName()
                );
        if (state.isCompletelyEmpty()) {
            return false;
        }
        closeOpenInventories(coordinates, true);
        extinguishHeatSource(steamerBlock.getRelative(BlockFace.DOWN));
        dropStoredItems(steamerBlock, state);
        activeStations.remove(coordinates);
        removeState(coordinates, true);
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SteamerGuiHolder holder)) {
            return;
        }
        openSessions.remove(holder.viewerId(), holder);
        if (holder.suppressSave()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        processExcessItems(player, event.getInventory());
        SteamerState state = saveInventory(holder.coordinates(), event.getInventory(), player.getUniqueId(), player.getName());
        if (shouldRemainActive(state, System.currentTimeMillis())) {
            activeStations.add(holder.coordinates());
            ensureTicker();
        } else if (state.isCompletelyEmpty()) {
            activeStations.remove(holder.coordinates());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SteamerGuiHolder)) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getInventory().getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < topSize && !ingredientSlotSet(event.getInventory()).contains(rawSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SteamerGuiHolder)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        Set<Integer> ingredientSlots = ingredientSlotSet(event.getInventory());
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot >= 0 && rawSlot < topSize && !ingredientSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean openGui(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            return false;
        }
        SteamerGuiHolder existingHolder = findOpenSession(coordinates);
        if (existingHolder != null && !player.getUniqueId().equals(existingHolder.viewerId())) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.in_use", Map.of());
            return true;
        }
        SteamerGuiHolder holder = new SteamerGuiHolder(player.getUniqueId(), coordinates);
        Inventory inventory = createInventory(holder);
        if (inventory == null) {
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.open_failed", Map.of());
            return false;
        }
        holder.attach(inventory);
        loadInventory(coordinates, inventory);
        openSessions.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.opened", Map.of());
        return true;
    }

    private Inventory createInventory(SteamerGuiHolder holder) {
        String title = MiniMessages.legacy(MiniMessages.parse(settingsService.steamerInventoryTitle()));
        return Bukkit.createInventory(holder, settingsService.steamerInventoryRows() * 9, title);
    }

    private void loadInventory(StationCoordinates coordinates, Inventory inventory) {
        if (coordinates == null || inventory == null) {
            return;
        }
        inventory.clear();
        SteamerState state = loadStateOrEmpty(coordinates);
        for (int slot : ingredientSlots(inventory)) {
            String source = state.slotSources().get(slot);
            if (Texts.isBlank(source)) {
                continue;
            }
            ItemStack itemStack = deserializeItem(state.slotItemData(slot));
            if (itemStack == null || itemStack.getType().isAir()) {
                ItemSource itemSource = ItemSourceUtil.parse(source);
                itemStack = itemSource == null ? null : itemSourceService.createItem(itemSource, 1);
            }
            if (itemStack != null && !itemStack.getType().isAir()) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    private boolean addFuel(Player player,
            StationCoordinates coordinates,
            Block heatSourceBlock,
            ItemStack hand,
            CookingSettingsService.SteamerFuelRule rule) {
        if (player == null || coordinates == null || hand == null || rule == null) {
            return false;
        }
        SteamerState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long currentBurning = state.burningUntilMs();
        long durationMs = Math.max(0L, rule.durationSeconds()) * 1000L;
        long newBurning = currentBurning > now ? currentBurning + durationMs : now + durationMs;
        state.setBurningUntilMs(newBurning);
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        CookingRuntimeUtil.takeOneFromMainHand(player);
        if (settingsService.steamerIgniteHeatSource()) {
            igniteHeatSource(heatSourceBlock, newBurning, now);
        }
        activeStations.add(coordinates);
        ensureTicker();
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.add_fuel", Map.of(
                "item", itemDisplayName(hand),
                "seconds", Math.max(0L, (newBurning - now) / 1000L)
        ));
        return true;
    }

    private boolean addMoisture(Player player,
            StationCoordinates coordinates,
            ItemStack hand,
            CookingSettingsService.SteamerMoistureRule rule) {
        if (player == null || coordinates == null || hand == null || rule == null) {
            return false;
        }
        SteamerState state = loadStateOrEmpty(coordinates);
        state.setMoisture(state.moisture() + Math.max(0, rule.moisture()));
        state.setPlayerContext(player.getUniqueId(), player.getName());
        saveState(coordinates, state);
        CookingRuntimeUtil.takeOneFromMainHand(player);
        if (rule.outputSource() != null) {
            ItemStack output = itemSourceService.createItem(rule.outputSource(), 1);
            if (output != null && !output.getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, output);
            }
        }
        if (state.moisture() > 0) {
            activeStations.add(coordinates);
            ensureTicker();
        }
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.add_moisture", Map.of(
                "item", itemDisplayName(hand),
                "moisture", Math.max(0, rule.moisture()),
                "total", state.moisture()
        ));
        return true;
    }

    private boolean showInfo(Player player, StationCoordinates coordinates) {
        if (player == null || coordinates == null) {
            return false;
        }
        SteamerState state = loadStateOrEmpty(coordinates);
        long now = System.currentTimeMillis();
        long remainingBurnTime = state.burningUntilMs() > now ? (state.burningUntilMs() - now) / 1000L : 0L;
        CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "steamer.info", Map.of(
                "burning_time", remainingBurnTime,
                "moisture", state.moisture(),
                "steam", state.steam(),
                "progress", calculateProgressStatus(state)
        ));
        return true;
    }

    private String calculateProgressStatus(SteamerState state) {
        if (state == null || state.slotSources().isEmpty()) {
            return messageService.message("steamer.progress_not_started");
        }
        int totalRequired = 0;
        int totalProgress = 0;
        int validIngredients = 0;
        boolean allCompleted = true;
        for (Map.Entry<Integer, String> entry : sortedSlots(state.slotSources()).entrySet()) {
            RecipeDocument recipe = recipeService.findSteamerRecipe(entry.getValue(), null);
            if (recipe == null) {
                continue;
            }
            int required = recipeService.steamerRequiredSteam(recipe);
            int progress = Math.min(required, state.progressAt(entry.getKey()));
            validIngredients++;
            totalRequired += required;
            totalProgress += progress;
            if (progress < required) {
                allCompleted = false;
            }
        }
        if (validIngredients <= 0) {
            return messageService.message("steamer.progress_not_started");
        }
        if (allCompleted) {
            return messageService.message("steamer.progress_completed");
        }
        if (totalRequired <= 0) {
            return "0.00%";
        }
        return String.format(Locale.ROOT, "%.2f%%", (double) totalProgress * 100.0D / (double) totalRequired);
    }

    private void ensureTicker() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        if (tickerTask != null && !tickerTask.isCancelled()) {
            return;
        }
        tickerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void ensureFlushTask() {
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
            return;
        }
        if (flushTask != null && !flushTask.isCancelled()) {
            return;
        }
        flushTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::flushDirtyStates,
                DIRTY_FLUSH_INTERVAL_TICKS,
                DIRTY_FLUSH_INTERVAL_TICKS
        );
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void cancelFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    private void flushDirtyStates() {
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
            return;
        }
        for (StationCoordinates coordinates : List.copyOf(dirtyStations)) {
            SteamerState state = runtimeStates.get(coordinates);
            if (state == null || state.isCompletelyEmpty()) {
                removeState(coordinates, true);
                continue;
            }
            if (stateStore.trySave(coordinates, serializeState(coordinates, state))) {
                dirtyStations.remove(coordinates);
            }
        }
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
        }
    }

    private void tick() {
        if (activeStations.isEmpty()) {
            cancelTicker();
            return;
        }
        long now = System.currentTimeMillis();
        for (StationCoordinates coordinates : List.copyOf(activeStations)) {
            processStation(coordinates, now);
        }
        if (activeStations.isEmpty()) {
            cancelTicker();
        }
    }

    private void processStation(StationCoordinates coordinates, long now) {
        Block block = coordinates == null ? null : coordinates.block();
        SteamerState state = loadStateOrEmpty(coordinates);
        if (block == null || !blockMatcher.matches(block, StationType.STEAMER)) {
            closeOpenInventories(coordinates, true);
            removeState(coordinates, true);
            activeStations.remove(coordinates);
            return;
        }
        boolean changed = false;
        Block heatSourceBlock = block.getRelative(BlockFace.DOWN);
        if (state.burningUntilMs() > 0L && now >= state.burningUntilMs()) {
            extinguishHeatSource(heatSourceBlock);
            state.setBurningUntilMs(0L);
            changed = true;
        }
        if (state.burningUntilMs() > now && state.moisture() > 0 && settingsService.steamerSteamProductionEfficiency() > 0) {
            int produced = Math.min(state.moisture(), settingsService.steamerSteamProductionEfficiency());
            if (produced > 0) {
                state.setMoisture(state.moisture() - produced);
                state.setSteam(state.steam() + produced);
                changed = true;
            }
        }
        if (processSteamConsumptionAndCooking(block, state)) {
            changed = true;
        }
        if (changed) {
            saveState(coordinates, state);
        }
        if (shouldRemainActive(state, now)) {
            activeStations.add(coordinates);
        } else {
            activeStations.remove(coordinates);
            if (state.isCompletelyEmpty()) {
                removeState(coordinates, true);
            }
        }
    }

    private boolean processSteamConsumptionAndCooking(Block steamerBlock, SteamerState state) {
        if (steamerBlock == null || state == null) {
            return false;
        }
        int baseConsumption = settingsService.steamerSteamConsumptionEfficiency();
        int conversionEfficiency = settingsService.steamerSteamConversionEfficiency();
        int currentSteam = state.steam();
        boolean changed = false;

        List<Integer> validSlots = new ArrayList<>();
        Map<Integer, RecipeDocument> recipesBySlot = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : sortedSlots(state.slotSources()).entrySet()) {
            RecipeDocument recipe = recipeService.findSteamerRecipe(entry.getValue(), null);
            if (recipe != null) {
                validSlots.add(entry.getKey());
                recipesBySlot.put(entry.getKey(), recipe);
            }
        }

        if (currentSteam <= 0) {
            if (settingsService.steamerResetProgressWhenSteamEmpty()) {
                for (Integer slot : validSlots) {
                    if (state.progressAt(slot) > 0) {
                        state.setProgress(slot, 0);
                        changed = true;
                    }
                }
            }
            return changed;
        }

        if (validSlots.isEmpty()) {
            int newSteam = Math.max(0, currentSteam - baseConsumption);
            if (newSteam != currentSteam) {
                state.setSteam(newSteam);
                changed = true;
            }
            return changed;
        }

        int ingredientConsumption = validSlots.size() * conversionEfficiency;
        int totalConsumption = baseConsumption + ingredientConsumption;
        if (currentSteam < totalConsumption) {
            int availableForIngredients = Math.max(0, currentSteam - baseConsumption);
            int actualIngredientConsumption = Math.min(availableForIngredients, ingredientConsumption);
            int newSteam = Math.max(0, currentSteam - baseConsumption - actualIngredientConsumption);
            if (newSteam != currentSteam) {
                state.setSteam(newSteam);
                changed = true;
            }
            if (ingredientConsumption > 0 && actualIngredientConsumption > 0) {
                double ratio = (double) actualIngredientConsumption / (double) ingredientConsumption;
                for (Integer slot : validSlots) {
                    int required = recipeService.steamerRequiredSteam(recipesBySlot.get(slot));
                    int additionalProgress = (int) Math.floor(conversionEfficiency * ratio);
                    if (additionalProgress <= 0) {
                        continue;
                    }
                    int newProgress = Math.min(required, state.progressAt(slot) + additionalProgress);
                    if (newProgress != state.progressAt(slot)) {
                        state.setProgress(slot, newProgress);
                        changed = true;
                    }
                }
            }
            return changed;
        }

        int newSteam = currentSteam - totalConsumption;
        if (newSteam != currentSteam) {
            state.setSteam(newSteam);
            changed = true;
        }
        for (Integer slot : validSlots) {
            RecipeDocument recipe = recipesBySlot.get(slot);
            int requiredSteam = recipeService.steamerRequiredSteam(recipe);
            int progress = state.progressAt(slot) + conversionEfficiency;
            if (progress >= requiredSteam) {
                completeSlot(steamerBlock, state, slot, recipe);
            } else {
                state.setProgress(slot, progress);
            }
            changed = true;
        }
        return changed;
    }

    private void completeSlot(Block steamerBlock, SteamerState state, int slot, RecipeDocument recipe) {
        Map<String, Object> outcome = recipeService.outcome(recipe, "result.output");
        List<Map<String, Object>> outputs = recipeService.outputs(outcome);
        List<String> actions = combineActions(recipeService.actions(recipe), recipeService.actions(outcome));
        Location rewardLocation = steamerBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        Player player = state.playerUuid() == null ? null : Bukkit.getPlayer(state.playerUuid());
        Map<String, Object> placeholders = Map.of(
                "recipe_id", recipe.id(),
                "station_type", StationType.STEAMER.folderName(),
                "slot_index", slot
        );

        if (!settingsService.steamerDropResult() && canStoreOutcomeInSlot(outputs)) {
            Map<String, Object> storedOutput = outputs.getFirst();
            String source = String.valueOf(storedOutput.getOrDefault("source", ""));
            if (Texts.isNotBlank(source)) {
                ItemStack storedItem = rewardService.createOutputItem(
                        recipe,
                        storedOutput,
                        player,
                        rewardLocation,
                        "cooking_steamer_complete",
                        placeholders
                );
                state.setSlotSource(slot, source);
                state.setSlotItem(slot, serializeItem(storedItem));
                state.setProgress(slot, 0);
                rewardService.deliver(
                        recipe,
                        player,
                        rewardLocation,
                        false,
                        List.of(),
                        actions,
                        "cooking_steamer_complete",
                        placeholders
                );
                return;
            }
        }

        rewardService.deliver(
                recipe,
                player,
                rewardLocation,
                settingsService.steamerDropResult(),
                outputs,
                actions,
                "cooking_steamer_complete",
                placeholders
        );
        state.removeSlot(slot);
    }

    private boolean canStoreOutcomeInSlot(List<Map<String, Object>> outputs) {
        if (outputs == null || outputs.size() != 1) {
            return false;
        }
        Map<String, Object> output = outputs.getFirst();
        if (output == null || output.isEmpty()) {
            return false;
        }
        if (ItemSourceUtil.parse(output.get("source")) == null) {
            return false;
        }
        if (output.containsKey("amount_range")) {
            return false;
        }
        Object chance = output.get("chance");
        if (chance != null && CookingRuntimeUtil.parseInteger(chance, 100) < 100) {
            return false;
        }
        return CookingRuntimeUtil.parseInteger(output.get("amount"), 1) == 1;
    }

    private List<String> combineActions(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return merged.isEmpty() ? List.of() : List.copyOf(merged);
    }

    private void dropStoredItems(Block steamerBlock, SteamerState state) {
        if (steamerBlock == null || state == null || steamerBlock.getWorld() == null) {
            return;
        }
        Location dropLocation = steamerBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Map.Entry<Integer, String> entry : sortedSlots(state.slotSources()).entrySet()) {
            ItemStack storedItem = deserializeItem(state.slotItemData(entry.getKey()));
            if (storedItem != null && !storedItem.getType().isAir()) {
                steamerBlock.getWorld().dropItemNaturally(dropLocation, storedItem);
                continue;
            }
            String dropSource = entry.getValue();
            RecipeDocument recipe = recipeService.findSteamerRecipe(dropSource, null);
            if (recipe != null && state.progressAt(entry.getKey()) >= recipeService.steamerRequiredSteam(recipe)) {
                Map<String, Object> outcome = recipeService.outcome(recipe, "result.output");
                List<Map<String, Object>> outputs = recipeService.outputs(outcome);
                if (canStoreOutcomeInSlot(outputs)) {
                    dropSource = String.valueOf(outputs.getFirst().getOrDefault("source", dropSource));
                }
            }
            ItemSource source = ItemSourceUtil.parse(dropSource);
            ItemStack itemStack = source == null ? null : itemSourceService.createItem(source, 1);
            if (itemStack != null && !itemStack.getType().isAir()) {
                steamerBlock.getWorld().dropItemNaturally(dropLocation, itemStack);
            }
        }
    }

    private SteamerState saveInventory(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        if (coordinates == null || inventory == null) {
            return new SteamerState();
        }
        SteamerState updated = snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
        saveState(coordinates, updated);
        return updated;
    }

    private SteamerState snapshotInventoryState(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        SteamerState previous = loadStateOrEmpty(coordinates);
        SteamerState updated = new SteamerState();
        updated.setPlayerContext(playerUuid, playerName);
        updated.setBurningUntilMs(previous.burningUntilMs());
        updated.setMoisture(previous.moisture());
        updated.setSteam(previous.steam());
        updated.clearSlots();
        if (inventory == null) {
            return updated;
        }
        Player player = playerUuid == null ? null : Bukkit.getPlayer(playerUuid);
        Set<Integer> ingredientSlots = ingredientSlotSet(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            if (!ingredientSlots.contains(slot)) {
                if (player != null) {
                    inventory.clear(slot);
                    InventoryItemUtil.giveOrDrop(player, itemStack);
                }
                continue;
            }
            String source = identifySource(itemStack);
            if (Texts.isBlank(source)) {
                if (player != null) {
                    InventoryItemUtil.giveOrDrop(player, itemStack);
                }
                continue;
            }
            updated.setSlotSource(slot, source);
            updated.setSlotItem(slot, serializeItem(itemStack));
            if (source.equals(previous.slotSources().get(slot))) {
                updated.setProgress(slot, previous.progressAt(slot));
            } else {
                updated.setProgress(slot, 0);
            }
        }
        return updated;
    }

    private void saveState(StationCoordinates coordinates, SteamerState state) {
        if (coordinates == null || state == null) {
            return;
        }
        if (state.isCompletelyEmpty()) {
            removeState(coordinates, true);
            return;
        }
        runtimeStates.put(coordinates, state);
        dirtyStations.add(coordinates);
        ensureFlushTask();
    }

    private Map<String, Object> serializeState(StationCoordinates coordinates, SteamerState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.STEAMER, coordinates);

        Map<String, Object> steamer = new LinkedHashMap<>();
        steamer.put("burning_until_ms", state.burningUntilMs());
        steamer.put("moisture", state.moisture());
        steamer.put("steam", state.steam());
        if (state.playerUuid() != null) {
            steamer.put("player_uuid", state.playerUuid().toString());
        }
        if (Texts.isNotBlank(state.playerName())) {
            steamer.put("player_name", state.playerName());
        }
        root.put("steamer", steamer);

        List<Map<String, Object>> guiSlots = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : sortedSlots(state.slotSources()).entrySet()) {
            if (Texts.isBlank(entry.getValue())) {
                continue;
            }
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", entry.getKey());
            slot.put("source", entry.getValue());
            Map<String, Object> item = state.slotItemData(entry.getKey());
            if (item != null && !item.isEmpty()) {
                slot.put("item", item);
            }
            guiSlots.add(slot);
        }
        if (!guiSlots.isEmpty()) {
            root.put("gui_slots", guiSlots);
        }

        List<Map<String, Object>> slotProgress = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : sortedProgress(state.slotProgress()).entrySet()) {
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("index", entry.getKey());
            progress.put("progress", Math.max(0, entry.getValue()));
            slotProgress.add(progress);
        }
        if (!slotProgress.isEmpty()) {
            root.put("slot_progress", slotProgress);
        }
        return root;
    }

    private SteamerState loadStateOrEmpty(StationCoordinates coordinates) {
        if (coordinates == null) {
            return new SteamerState();
        }
        SteamerState cached = runtimeStates.get(coordinates);
        if (cached != null) {
            return cached;
        }
        SteamerState loaded = readState(stateStore.load(coordinates));
        SteamerState existing = runtimeStates.putIfAbsent(coordinates, loaded);
        return existing == null ? loaded : existing;
    }

    private void cacheState(StationCoordinates coordinates, SteamerState state) {
        if (coordinates == null || state == null) {
            return;
        }
        runtimeStates.put(coordinates, state);
        dirtyStations.remove(coordinates);
    }

    private void removeState(StationCoordinates coordinates, boolean deleteFile) {
        if (coordinates == null) {
            return;
        }
        runtimeStates.remove(coordinates);
        dirtyStations.remove(coordinates);
        if (deleteFile) {
            stateStore.tryDelete(coordinates);
        }
        if (dirtyStations.isEmpty()) {
            cancelFlushTask();
        }
    }

    private SteamerState readState(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        SteamerState state = new SteamerState();
        if (section == null || !StationType.STEAMER.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return state;
        }
        state.setBurningUntilMs(CookingRuntimeUtil.parseLong(section.get("steamer.burning_until_ms"), 0L));
        state.setMoisture(section.getInt("steamer.moisture", 0));
        state.setSteam(section.getInt("steamer.steam", 0));
        state.setPlayerContext(CookingRuntimeUtil.parseUuid(section.getString("steamer.player_uuid", "")), section.getString("steamer.player_name", ""));
        for (Map<?, ?> raw : section.getMapList("gui_slots")) {
            Map<String, Object> slot = MapYamlSection.normalizeMap(raw);
            int index = CookingRuntimeUtil.parseInteger(slot.get("index"), -1);
            String source = String.valueOf(slot.getOrDefault("source", ""));
            if (index >= 0 && Texts.isNotBlank(source)) {
                state.setSlotSource(index, source);
                Object rawItem = ConfigNodes.toPlainData(slot.get("item"));
                if (rawItem instanceof Map<?, ?> itemMap) {
                    state.setSlotItem(index, MapYamlSection.normalizeMap(itemMap));
                }
            }
        }
        for (Map<?, ?> raw : section.getMapList("slot_progress")) {
            Map<String, Object> progress = emaki.jiuwu.craft.corelib.yaml.MapYamlSection.normalizeMap(raw);
            int index = CookingRuntimeUtil.parseInteger(progress.get("index"), -1);
            int value = CookingRuntimeUtil.parseInteger(progress.get("progress"), 0);
            if (index >= 0) {
                state.setProgress(index, value);
            }
        }
        return state;
    }

    private boolean shouldRemainActive(SteamerState state, long now) {
        return state != null && (state.burningUntilMs() > now
                || state.moisture() > 0
                || state.steam() > 0
                || hasValidIngredients(state));
    }

    private boolean hasValidIngredients(SteamerState state) {
        if (state == null || state.slotSources().isEmpty()) {
            return false;
        }
        for (String source : state.slotSources().values()) {
            if (recipeService.findSteamerRecipe(source, null) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeatSourceBlock(Block block) {
        if (block == null) {
            return false;
        }
        for (ItemSource source : settingsService.steamerHeatSources()) {
            if (blockMatcher.matches(block, source)) {
                return true;
            }
        }
        return false;
    }

    private CookingSettingsService.SteamerFuelRule matchFuelRule(ItemStack itemStack) {
        ItemSource identified = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        if (identified == null) {
            return null;
        }
        for (CookingSettingsService.SteamerFuelRule rule : settingsService.steamerFuels()) {
            if (rule != null && ItemSourceUtil.matches(rule.source(), identified)) {
                return rule;
            }
        }
        return null;
    }

    private CookingSettingsService.SteamerMoistureRule matchMoistureRule(ItemStack itemStack) {
        ItemSource identified = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        if (identified == null) {
            return null;
        }
        for (CookingSettingsService.SteamerMoistureRule rule : settingsService.steamerMoistureSources()) {
            if (rule != null && ItemSourceUtil.matches(rule.inputSource(), identified)) {
                return rule;
            }
        }
        return null;
    }

    private void igniteHeatSource(Block heatSourceBlock, long burningUntilMs, long now) {
        if (heatSourceBlock == null) {
            return;
        }
        BlockData blockData = heatSourceBlock.getBlockData();
        if (blockData instanceof Campfire campfire) {
            campfire.setLit(true);
            heatSourceBlock.setBlockData(campfire);
            return;
        }
        if (heatSourceBlock.getState() instanceof Furnace furnace) {
            long remainingTicks = Math.max(0L, (burningUntilMs - now) / 50L);
            furnace.setBurnTime((short) Math.min(Short.MAX_VALUE, remainingTicks));
            furnace.update();
        }
    }

    private void extinguishHeatSource(Block heatSourceBlock) {
        if (heatSourceBlock == null) {
            return;
        }
        BlockData blockData = heatSourceBlock.getBlockData();
        if (blockData instanceof Campfire campfire) {
            campfire.setLit(false);
            heatSourceBlock.setBlockData(campfire);
            return;
        }
        if (heatSourceBlock.getState() instanceof Furnace furnace) {
            furnace.setBurnTime((short) 0);
            furnace.update();
        }
    }

    private void processExcessItems(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        Set<Integer> ingredientSlots = ingredientSlotSet(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            if (!ingredientSlots.contains(slot)) {
                inventory.clear(slot);
                InventoryItemUtil.giveOrDrop(player, itemStack);
                continue;
            }
            if (itemStack.getAmount() <= 1) {
                continue;
            }
            ItemStack excess = itemStack.clone();
            excess.setAmount(itemStack.getAmount() - 1);
            itemStack.setAmount(1);
            inventory.setItem(slot, itemStack);
            InventoryItemUtil.giveOrDrop(player, excess);
        }
    }

    private void closeAllOpenInventories(boolean suppressSave) {
        for (SteamerGuiHolder holder : List.copyOf(openSessions.values())) {
            if (holder == null) {
                continue;
            }
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null
                    && viewer.getOpenInventory().getTopInventory().getHolder() == holder) {
                viewer.closeInventory();
            }
        }
        if (suppressSave) {
            openSessions.clear();
        }
    }

    private void closeOpenInventories(StationCoordinates coordinates, boolean suppressSave) {
        if (coordinates == null) {
            return;
        }
        for (SteamerGuiHolder holder : List.copyOf(openSessions.values())) {
            if (holder == null || !coordinates.equals(holder.coordinates())) {
                continue;
            }
            holder.setSuppressSave(suppressSave);
            Player viewer = Bukkit.getPlayer(holder.viewerId());
            if (viewer != null && viewer.getOpenInventory() != null
                    && viewer.getOpenInventory().getTopInventory().getHolder() == holder) {
                viewer.closeInventory();
            } else {
                openSessions.remove(holder.viewerId(), holder);
            }
        }
    }

    private SteamerGuiHolder findOpenSession(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        for (SteamerGuiHolder holder : openSessions.values()) {
            if (holder != null && coordinates.equals(holder.coordinates())) {
                return holder;
            }
        }
        return null;
    }

    private String identifySource(ItemStack itemStack) {
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    private Map<String, Object> serializeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }
        Object plain = ConfigNodes.toPlainData(itemStack.serialize());
        if (!(plain instanceof Map<?, ?> itemMap)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(itemMap));
    }

    private ItemStack deserializeItem(Map<String, Object> serializedItem) {
        if (serializedItem == null || serializedItem.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserialize(new LinkedHashMap<>(serializedItem));
        } catch (Exception _) {
            return null;
        }
    }

    private String itemDisplayName(ItemStack itemStack) {
        ItemSource source = itemStack == null || itemStack.getType().isAir() ? null : itemSourceService.identifyItem(itemStack);
        String displayName = itemSourceService.displayName(source);
        return Texts.isBlank(displayName)
                ? (itemStack == null || itemStack.getType() == null ? "" : itemStack.getType().name())
                : displayName;
    }

    private List<Integer> ingredientSlots(Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        int size = inventory.getSize();
        List<Integer> configured = settingsService.steamerIngredientSlots();
        List<Integer> slots = new ArrayList<>();
        for (Integer slot : configured) {
            if (slot != null && slot >= 0 && slot < size) {
                slots.add(slot);
            }
        }
        return slots.isEmpty() ? List.of() : List.copyOf(slots);
    }

    private Set<Integer> ingredientSlotSet(Inventory inventory) {
        return Set.copyOf(ingredientSlots(inventory));
    }

    private Map<Integer, String> sortedSlots(Map<Integer, String> slots) {
        Map<Integer, String> sorted = new LinkedHashMap<>();
        if (slots == null || slots.isEmpty()) {
            return sorted;
        }
        slots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private Map<Integer, Integer> sortedProgress(Map<Integer, Integer> progress) {
        Map<Integer, Integer> sorted = new LinkedHashMap<>();
        if (progress == null || progress.isEmpty()) {
            return sorted;
        }
        progress.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private static final class SteamerGuiHolder implements InventoryHolder {

        private final UUID viewerId;
        private final StationCoordinates coordinates;
        private Inventory inventory;
        private boolean suppressSave;

        private SteamerGuiHolder(UUID viewerId, StationCoordinates coordinates) {
            this.viewerId = viewerId;
            this.coordinates = coordinates;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID viewerId() {
            return viewerId;
        }

        private StationCoordinates coordinates() {
            return coordinates;
        }

        private boolean suppressSave() {
            return suppressSave;
        }

        private void setSuppressSave(boolean suppressSave) {
            this.suppressSave = suppressSave;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SteamerState {

        private long burningUntilMs;
        private int moisture;
        private int steam;
        private UUID playerUuid;
        private String playerName = "";
        private final Map<Integer, String> slotSources = new LinkedHashMap<>();
        private final Map<Integer, Map<String, Object>> slotItems = new LinkedHashMap<>();
        private final Map<Integer, Integer> slotProgress = new LinkedHashMap<>();

        private long burningUntilMs() {
            return burningUntilMs;
        }

        private void setBurningUntilMs(long burningUntilMs) {
            this.burningUntilMs = Math.max(0L, burningUntilMs);
        }

        private int moisture() {
            return Math.max(0, moisture);
        }

        private void setMoisture(int moisture) {
            this.moisture = Math.max(0, moisture);
        }

        private int steam() {
            return Math.max(0, steam);
        }

        private void setSteam(int steam) {
            this.steam = Math.max(0, steam);
        }

        private UUID playerUuid() {
            return playerUuid;
        }

        private String playerName() {
            return playerName;
        }

        private void setPlayerContext(UUID playerUuid, String playerName) {
            if (playerUuid != null) {
                this.playerUuid = playerUuid;
            }
            this.playerName = Texts.toStringSafe(playerName);
        }

        private Map<Integer, String> slotSources() {
            return slotSources;
        }

        private Map<Integer, Integer> slotProgress() {
            return slotProgress;
        }

        private Map<String, Object> slotItemData(int slot) {
            return slotItems.get(slot);
        }

        private int progressAt(int slot) {
            return Math.max(0, slotProgress.getOrDefault(slot, 0));
        }

        private void setProgress(int slot, int progress) {
            if (slot < 0) {
                return;
            }
            int normalized = Math.max(0, progress);
            if (normalized <= 0) {
                slotProgress.remove(slot);
                return;
            }
            slotProgress.put(slot, normalized);
        }

        private void setSlotSource(int slot, String source) {
            if (slot < 0 || Texts.isBlank(source)) {
                return;
            }
            slotSources.put(slot, source);
        }

        private void setSlotItem(int slot, Map<String, Object> serializedItem) {
            if (slot < 0) {
                return;
            }
            if (serializedItem == null || serializedItem.isEmpty()) {
                slotItems.remove(slot);
                return;
            }
            slotItems.put(slot, Map.copyOf(serializedItem));
        }

        private void removeSlot(int slot) {
            slotSources.remove(slot);
            slotItems.remove(slot);
            slotProgress.remove(slot);
        }

        private void clearSlots() {
            slotSources.clear();
            slotItems.clear();
            slotProgress.clear();
        }

        private boolean isCompletelyEmpty() {
            return burningUntilMs() <= 0L && moisture() <= 0 && steam() <= 0 && slotSources.isEmpty();
        }
    }
}
