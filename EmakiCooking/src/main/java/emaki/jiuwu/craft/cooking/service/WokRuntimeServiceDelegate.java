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

final class WokRuntimeServiceDelegate {

    private final WokRuntimeService ownerRef;

    WokRuntimeServiceDelegate(WokRuntimeService ownerRef) {
        this.ownerRef = ownerRef;
    }

    void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        ownerRef.completionCoordinator = completionCoordinator;
        if (completionCoordinator != null) {
            completionCoordinator.register(ownerRef.completionStateAccess());
        }
    }

    boolean restoreStoredState(StationCoordinates coordinates, YamlSection section) {
        if (coordinates == null) {
            return false;
        }
        WokRuntimeService.WokState state = ownerRef.readState(section);
        ItemSourceRef stationSource = ownerRef.stateStore.stationSource(section);
        Block block = coordinates.block();
        if (state == null || !state.hasIngredients()) {
            ownerRef.displayService.removeStation(StationType.WOK, coordinates);
            ownerRef.textDisplayService.removeStation(StationType.WOK, coordinates);
            return false;
        }
        if (!ownerRef.blockMatcher.matches(block, StationType.WOK, stationSource)) {
            ownerRef.displayService.removeStation(StationType.WOK, coordinates);
            ownerRef.textDisplayService.removeStation(StationType.WOK, coordinates);
            ownerRef.plugin.messageService().warning("console.station_restore_skipped_mismatch", Map.of(
                    "type", "wok",
                    "coordinate", coordinates.runtimeKey()));
            return false;
        }
        ownerRef.refreshDisplays(coordinates, state);
        return true;
    }

    void unloadStoredState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        ownerRef.displayService.removeStation(StationType.WOK, coordinates);
        ownerRef.textDisplayService.removeStation(StationType.WOK, coordinates);
    }

    boolean handleInspect(StationInteraction interaction, Player player, WokRuntimeService.WokState state, int heatLevel) {
        if (state == null || !state.hasIngredients()) {
            CookingRuntimeUtil.sendActionBar(ownerRef.plugin, player, ownerRef.messageService, "wok.no_item", Map.of());
            interaction.cancel();
            return true;
        }
        ownerRef.showContents(player, state, heatLevel);
        interaction.cancel();
        return true;
    }

    boolean handleServe(StationInteraction interaction, Block block, Player player, StationCoordinates coordinates, WokRuntimeService.WokState state, int heatLevel, boolean servingWithBowl) {
        if (!player.hasPermission(CookingPermissions.WOK_SERVE)
                && !player.hasPermission(CookingPermissions.ADMIN)) {
            ownerRef.messageService.send(player, CommandKeys.NO_PERMISSION);
            interaction.cancel();
            return true;
        }
        if (state == null || !state.hasIngredients()) {
            ownerRef.debugStation("station.wok_serve", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "result", "rejected_no_state",
                    "with_bowl", servingWithBowl,
                    "stir", 0,
                    "recipe", ""
            ));
            CookingRuntimeUtil.sendActionBar(ownerRef.plugin, player, ownerRef.messageService, "wok.no_item", Map.of());
            interaction.cancel();
            return true;
        }
        if (ownerRef.tryServe(player, block, coordinates, state, heatLevel, servingWithBowl)) {
            interaction.cancel();
            return true;
        }
        ownerRef.debugStation("station.wok_serve", Map.of(
                "player", player.getName(),
                "station", coordinates.runtimeKey(),
                "result", state.totalStirCount() <= 0 ? "rejected_never_stirred" : "rejected_submit_failed",
                "with_bowl", servingWithBowl,
                "stir", state.totalStirCount(),
                "recipe", ownerRef.describeRecipe(ownerRef.findMatchingRecipe(state, player, heatLevel))
        ));
        if (servingWithBowl) {
            CookingRuntimeUtil.sendActionBar(ownerRef.plugin, player, ownerRef.messageService, "wok.no_recipe", Map.of());
            interaction.cancel();
            return true;
        }
        return false;
    }

    boolean handleAddIngredient(StationInteraction interaction, Block block, Player player, StationCoordinates coordinates, WokRuntimeService.WokState state, int heatLevel, ItemStack hand) {
        if (!ownerRef.settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_ADD_INGREDIENT,
                interaction)) {
            return false;
        }
        String source = ownerRef.identifySource(hand);
        if (Texts.isBlank(source)) {
            return false;
        }
        if (state == null || !state.hasIngredients()) {
            return ownerRef.addFirstIngredient(interaction, block, player, coordinates, state, heatLevel, source, hand);
        }
        return ownerRef.appendIngredient(interaction, block, player, coordinates, state, heatLevel, source, hand);
    }

    boolean handleReturnIngredient(StationInteraction interaction, Player player, StationCoordinates coordinates, WokRuntimeService.WokState state) {
        if (state == null || !state.hasIngredients()) {
            return false;
        }
        if (!ownerRef.settingsService.matchesInteraction(
                StationType.WOK,
                CookingSettingsService.INTERACTION_RETURN_INGREDIENT,
                interaction)) {
            return false;
        }
        if (state.totalStirCount() > 0 && ownerRef.settingsService.wokScaldDamageEnabled()) {
            int damage = ownerRef.settingsService.wokScaldDamageValue();
            if (damage > 0) {
                player.damage(damage);
                CookingRuntimeUtil.sendActionBar(ownerRef.plugin, player, ownerRef.messageService, "wok.scalded", Map.of("damage", damage));
            }
        }
        WokRuntimeService.WokIngredientState removed = ownerRef.returnLastIngredient(player, coordinates, state);
        if (removed != null) {
            CookingRuntimeUtil.sendActionBar(ownerRef.plugin, player, ownerRef.messageService, "wok.ingredient_returned", Map.of("item", ownerRef.itemDisplayName(removed.source())));
            interaction.cancel();
            return true;
        }
        return false;
    }

    boolean handleBreak(StationBreakContext context) {
        Block block = context.block();
        if (block == null || !ownerRef.blockMatcher.matches(context, StationType.WOK)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        ownerRef.stateStore.rememberStationSource(coordinates, context.stationSource());
        if (ownerRef.completionCoordinator != null && ownerRef.completionCoordinator.hasActive(StationType.WOK, coordinates)) {
            return true;
        }
        WokRuntimeService.WokState state = ownerRef.readState(ownerRef.stateStore.load(coordinates));
        if (state == null || !state.hasIngredients()) {
            ownerRef.debugStation("station.break_without_state", Map.of(
                    "station", coordinates.runtimeKey(),
                    "type", StationType.WOK.folderName()
            ));
            ownerRef.clearRuntimeVisuals(coordinates);
            return false;
        }
        Location dropLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (WokRuntimeService.WokIngredientState ingredient : state.ingredients()) {
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
                ItemSourceRef source = ItemSourceUtil.parse(ingredient.source());
                ItemStack itemStack = source == null ? null : ownerRef.itemSourceService.createItem(source, fallbackAmount);
                if (itemStack != null && !itemStack.getType().isAir()) {
                    dropLocation.getWorld().dropItemNaturally(dropLocation, itemStack);
                }
            }
        }
        ownerRef.clearState(coordinates);
        return true;
    }

    Optional<StationSnapshot> snapshotAt(StationCoordinates coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        WokRuntimeService.WokState state = ownerRef.readState(ownerRef.stateStore.load(coordinates));
        if (state == null || !state.hasIngredients()) {
            return Optional.empty();
        }
        Block block = coordinates.block();
        int heatLevel = block == null ? 0 : ownerRef.resolveHeatLevel(block.getRelative(BlockFace.DOWN));
        WokRuntimeService.WokIngredientState first = state.ingredients().get(0);
        RecipeDocument recipe = ownerRef.predictRecipe(state, heatLevel);
        int target = recipe == null ? 0 : ownerRef.recipeService.wokStirTotalMin(recipe);
        int current = state.totalStirCount();
        double percent = target > 0 ? Math.min(100.0D, (double) current * 100.0D / (double) target) : 0.0D;
        return Optional.of(new StationSnapshot(
                StationType.WOK,
                coordinates.world(), coordinates.x(), coordinates.y(), coordinates.z(),
                CookingRuntimeUtil.resolveBlockId(ownerRef.plugin, block),
                CookingRuntimeUtil.resolveBlockId(ownerRef.plugin, block == null ? null : block.getRelative(BlockFace.DOWN)),
                false,
                0L,
                heatLevel, 0, 0,
                MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(first.source()).orElse("")),
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

    void showContents(Player player, WokRuntimeService.WokState state, int heatLevel) {
        ownerRef.messageService.sendRaw(player, ownerRef.messageService.message("wok.info_header"));
        int index = 1;
        for (WokRuntimeService.WokIngredientState ingredient : state.ingredients()) {
            ownerRef.messageService.sendRaw(player, ownerRef.messageService.message("wok.info_line", Map.of(
                    "index", index,
                    "item", ownerRef.itemDisplayName(ingredient.source()),
                    "amount", ingredient.amount(),
                    "stir", ingredient.stirTimes()
            )));
            index++;
        }
        ownerRef.messageService.sendRaw(player, ownerRef.messageService.message("wok.info_footer", Map.of(
                "count", state.totalStirCount(),
                "heat", heatLevel
        )));
    }

    Map<String, Object> invalidOutcome(RecipeDocument recipe) {
        Map<String, Object> configured = ownerRef.recipeService.outcome(recipe, "result.invalid");
        if (!configured.isEmpty()) {
            return configured;
        }
        if (Texts.isNotBlank(ownerRef.settingsService.wokInvalidResultSource())) {
            return Map.of("item_source", ownerRef.settingsService.wokInvalidResultSource(), "amount", 1);
        }
        if (Texts.isNotBlank(ownerRef.settingsService.wokFailureOutputSource())) {
            return Map.of("item_source", ownerRef.settingsService.wokFailureOutputSource(), "amount", 1);
        }
        return Map.of();
    }

    void completeWithCustomSource(Player player, Block block, StationCoordinates coordinates, WokRuntimeService.WokState state, boolean consumeBowl, String source, boolean dropResult, String phase, String messageKey, Map<String, ?> replacements) {
        CookingCompletionRequest.PlayerInventoryInput bowlInput = consumeBowl
                ? CookingCompletionRequest.PlayerInventoryInput.mainHand(player, 1, "wok serving bowl")
                : null;
        boolean accepted = state != null
                && (!consumeBowl || bowlInput != null)
                && ownerRef.completionCoordinator != null
                && ownerRef.completionCoordinator.submit(new CookingCompletionRequest(
                        "serve:" + state.lastStirTimeMs() + ":" + state.totalStirCount() + ":invalid",
                        StationType.WOK,
                        coordinates,
                        serializeState(coordinates, state),
                        CookingCompletionOperation.CommitMode.DELETE,
                        Map.of(),
                        null,
                        player,
                        block.getLocation().add(0.5D, 1.0D, 0.5D),
                        dropResult,
                        List.of(),
                        List.of(Map.of("item_sources", List.of(source), "amount", 1)),
                        List.of(),
                        phase,
                        Map.of("station_type", StationType.WOK.folderName()),
                        bowlInput == null ? List.of() : List.of(bowlInput),

                        null
                ));
        if (accepted) {
            CookingRuntimeUtil.sendActionBar(ownerRef.plugin, player, ownerRef.messageService, messageKey, replacements);
        }
    }

    String determineOutcomeBranch(WokRuntimeService.WokState state, RecipeDocument recipe) {
        long now = System.currentTimeMillis();
        if (state.lastStirTimeMs() > 0L
                && ownerRef.settingsService.wokTimeoutMs() > 0L
                && now - state.lastStirTimeMs() > ownerRef.settingsService.wokTimeoutMs()) {
            return "overcooked";
        }
        if (state.totalStirCount() < ownerRef.recipeService.wokStirTotalMin(recipe)) {
            return "undercooked";
        }
        if (state.totalStirCount() > ownerRef.recipeService.wokStirTotalMax(recipe)) {
            return "overcooked";
        }
        if (ownerRef.settingsService.wokFailureEnabled()
                && ownerRef.settingsService.wokFailureChance() > 0
                && ThreadLocalRandom.current().nextInt(100) < ownerRef.settingsService.wokFailureChance()) {
            return "invalid";
        }

        int lessThan = 0;
        int greaterThan = 0;
        List<Map<String, Object>> expected = ownerRef.recipeService.wokIngredients(recipe);
        for (int index = 0; index < Math.min(expected.size(), state.ingredients().size()); index++) {
            Map<String, Object> ingredient = expected.get(index);
            int actualStirs = state.ingredients().get(index).stirTimes();
            int comparison = ownerRef.recipeService.compareWokStirRule(String.valueOf(ingredient.getOrDefault("stir_rule", "0")), actualStirs);
            if (comparison < 0) {
                lessThan++;
            } else if (comparison > 0) {
                greaterThan++;
            }
        }
        int mismatchCount = lessThan + greaterThan;
        if (mismatchCount <= ownerRef.recipeService.wokFaultTolerance(recipe)) {
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

    RecipeDocument findMatchingRecipe(WokRuntimeService.WokState state, Player player, int heatLevel) {
        for (RecipeDocument recipe : ownerRef.recipeService.wokRecipes()) {
            if (recipe == null) {
                continue;
            }
            if (!ownerRef.recipeService.canUseRecipe(recipe, player)) {
                continue;
            }
            if (ownerRef.recipeService.wokHeatLevel(recipe) > 0 && ownerRef.recipeService.wokHeatLevel(recipe) != heatLevel) {
                continue;
            }
            List<Map<String, Object>> expected = ownerRef.recipeService.wokIngredients(recipe);
            if (expected.size() != state.ingredients().size()) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < expected.size(); index++) {
                Map<String, Object> ingredient = expected.get(index);
                String expectedSource = ownerRef.resolveIngredientSource(ingredient);
                int expectedAmount = CookingRuntimeUtil.parseInteger(ingredient.get("amount"), 1);
                WokRuntimeService.WokIngredientState actual = state.ingredients().get(index);
                if (!ownerRef.sourceMatches(expectedSource, actual.source()) || expectedAmount != actual.amount()) {
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

    WokRuntimeService.WokIngredientState returnLastIngredient(Player player, StationCoordinates coordinates, WokRuntimeService.WokState state) {
        if (state == null || !state.hasIngredients()) {
            return null;
        }
        List<WokRuntimeService.WokIngredientState> updatedIngredients = new ArrayList<>(state.ingredients());
        int lastIndex = updatedIngredients.size() - 1;
        if (lastIndex < 0) {
            return null;
        }
        WokRuntimeService.WokIngredientState last = updatedIngredients.get(lastIndex);
        ItemStack itemStack = last.lastStoredItem();
        if (itemStack == null || itemStack.getType().isAir()) {
            ItemSourceRef source = ItemSourceUtil.parse(last.source());
            itemStack = source == null ? null : ownerRef.itemSourceService.createItem(source, 1);
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        InventoryItemUtil.giveOrDrop(player, itemStack);
        if (last.amount() <= 1) {
            updatedIngredients.remove(lastIndex);
        } else {
            updatedIngredients.set(lastIndex, new WokRuntimeService.WokIngredientState(
                    last.source(),
                    last.amount() - 1,
                    last.stirTimes(),
                    ownerRef.removeLastItemData(last.itemData())
            ));
        }
        if (updatedIngredients.isEmpty()) {
            ownerRef.clearState(coordinates);
        } else {
            WokRuntimeService.WokState updated = new WokRuntimeService.WokState(updatedIngredients, state.totalStirCount(), state.lastStirTimeMs(), state.lastStirActionMs());
            ownerRef.saveState(coordinates, updated);
            ownerRef.refreshDisplays(coordinates, updated);
        }
        return last;
    }

    int resolveHeatLevel(Block block) {
        if (block == null) {
            return 0;
        }
        int resolved = 0;
        for (CookingSettingsService.HeatLevelRule rule : ownerRef.settingsService.wokHeatLevels()) {
            if (rule == null || rule.source() == null) {
                continue;
            }
            if (ownerRef.matchesHeatLevelRule(block, rule)) {
                resolved = Math.max(resolved, rule.level());
            }
        }
        return resolved;
    }

    boolean matchesHeatLevelRule(Block block, CookingSettingsService.HeatLevelRule rule) {
        return rule != null
                && (ownerRef.matchesSource(block, rule.source())
                || ownerRef.matchesSource(block, rule.litSource())
                || ownerRef.matchesSource(block, rule.unlitSource()));
    }

    void setWokHeatSourceLit(Block wokBlock, boolean lit) {
        if (!ownerRef.settingsService.wokIgniteHeatSource() || wokBlock == null) {
            return;
        }
        Block heatSourceBlock = wokBlock.getRelative(BlockFace.DOWN);
        if (heatSourceBlock == null || ownerRef.resolveHeatLevel(heatSourceBlock) <= 0) {
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
        if (!directStateChanged && !ownerRef.blockMatcher.setCustomLit(heatSourceBlock, lit)) {
            ownerRef.applyConfiguredHeatSourceTransition(heatSourceBlock, lit);
        }
    }

    boolean applyConfiguredHeatSourceTransition(Block block, boolean lit) {
        if (block == null) {
            return false;
        }
        for (CookingSettingsService.HeatLevelRule rule : ownerRef.settingsService.wokHeatLevels()) {
            if (rule == null) {
                continue;
            }
            ItemSourceRef target = lit ? rule.litSource() : rule.unlitSource();
            if (target == null) {
                continue;
            }
            if (ownerRef.blockMatcher.matches(block, target)) {
                return true;
            }
            if (lit && (ownerRef.matchesSource(block, rule.source()) || ownerRef.matchesSource(block, rule.unlitSource()))) {
                return ownerRef.blockMatcher.place(block, target);
            }
            if (!lit && ownerRef.matchesSource(block, rule.litSource())) {
                return ownerRef.blockMatcher.place(block, target);
            }
        }
        return false;
    }

    boolean matchesSource(Block block, ItemSourceRef source) {
        return block != null && source != null && ownerRef.blockMatcher.matches(block, source);
    }

    boolean isSpatula(ItemStack itemStack) {
        ItemSourceRef source = itemStack == null || itemStack.getType().isAir() ? null : ownerRef.itemSourceService.identifyItem(itemStack);
        if (source == null) {
            return false;
        }
        return CookingMatchers.accepts(ownerRef.settingsService.wokSpatulaRequirement(), itemStack, source, null);
    }

    boolean isPlainBowl(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemSourceRef source = ownerRef.itemSourceService.identifyItem(itemStack);
        return ItemSourceUtil.matches(WokRuntimeService.BOWL_SOURCE, source);
    }

    String identifySource(ItemStack itemStack) {
        ItemSourceRef source = itemStack == null || itemStack.getType().isAir() ? null : ownerRef.itemSourceService.identifyItem(itemStack);
        return source == null ? "" : Texts.toStringSafe(ItemSourceUtil.toShorthand(source));
    }

    String itemDisplayName(String source) {
        String displayName = EmakiCoreLibApi.itemDisplayName(source).orElse("");
        return Texts.isBlank(displayName) ? source : displayName;
    }

    String describeRecipe(RecipeDocument recipe) {
        return recipe == null ? "none" : recipe.id();
    }

    void damageHeldTool(Player player, ItemStack itemStack, int amount) {
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

    void saveState(StationCoordinates coordinates, WokRuntimeService.WokState state) {
        ownerRef.stateStore.saveAsync(coordinates, serializeState(coordinates, state));
    }

    void debugStation(String langKey, Map<String, ?> replacements) {
        if (ownerRef.plugin == null || ownerRef.plugin.debugLogger() == null) {
            return;
        }
        ownerRef.plugin.debugLogger().log("station", (UUID) null, langKey, replacements);
    }

    void debugStir(String langKey, Map<String, ?> replacements) {
        if (ownerRef.plugin == null || ownerRef.plugin.debugLogger() == null) {
            return;
        }
        ownerRef.plugin.debugLogger().log("stir", (UUID) null, langKey, replacements);
    }

    Map<String, Object> serializeState(StationCoordinates coordinates, WokRuntimeService.WokState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.WOK, coordinates);

        List<Map<String, Object>> ingredients = new ArrayList<>();
        for (WokRuntimeService.WokIngredientState ingredient : state.ingredients()) {
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
        return root;
    }

    RecipeDocument predictRecipe(WokRuntimeService.WokState state, int heatLevel) {
        if (state == null || !state.hasIngredients()) {
            return null;
        }
        List<CookingRecipeService.WokIngredientInput> actual = new ArrayList<>();
        for (WokRuntimeService.WokIngredientState ingredient : state.ingredients()) {
            actual.add(new CookingRecipeService.WokIngredientInput(ingredient.source(), ingredient.amount()));
        }
        for (RecipeDocument recipe : ownerRef.recipeService.wokRecipes()) {
            if (recipe == null || !ownerRef.recipeService.canUseRecipe(recipe, null)) {
                continue;
            }
            if (ownerRef.recipeService.wokHeatLevel(recipe) > 0 && heatLevel > 0 && ownerRef.recipeService.wokHeatLevel(recipe) != heatLevel) {
                continue;
            }
            if (ownerRef.ingredientsMatchRecipePrefix(recipe, actual)) {
                return recipe;
            }
        }
        return null;
    }

    boolean ingredientsMatchRecipePrefix(RecipeDocument recipe, List<CookingRecipeService.WokIngredientInput> actual) {
        List<Map<String, Object>> expected = ownerRef.recipeService.wokIngredients(recipe);
        if (expected.isEmpty() || actual.size() > expected.size()) {
            return false;
        }
        for (int index = 0; index < actual.size(); index++) {
            String expectedSource = ownerRef.resolveIngredientSource(expected.get(index));
            if (!ownerRef.sourceMatches(expectedSource, actual.get(index).source())) {
                return false;
            }
        }
        return true;
    }

    String nextStepHint(WokRuntimeService.WokState state, RecipeDocument recipe) {
        List<Map<String, Object>> expected = ownerRef.recipeService.wokIngredients(recipe);
        boolean allIngredientsPlaced = state.ingredients().size() >= expected.size();
        if (allIngredientsPlaced) {
            boolean lastAmountComplete = true;
            int lastIndex = expected.size() - 1;
            if (lastIndex >= 0 && lastIndex < state.ingredients().size()) {
                int expectedAmount = CookingRuntimeUtil.parseInteger(expected.get(lastIndex).get("amount"), 1);
                lastAmountComplete = state.ingredients().get(lastIndex).amount() >= expectedAmount;
            }
            if (lastAmountComplete && state.totalStirCount() >= ownerRef.recipeService.wokStirTotalMin(recipe)) {
                return ownerRef.messageService.message("text_display.wok.hint_serve");
            }
            return ownerRef.messageService.message("text_display.wok.hint_stir");
        }
        return ownerRef.messageService.message("text_display.wok.hint_add");
    }

    void appendLine(StringBuilder builder, String line) {
        if (Texts.isBlank(line)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

}
