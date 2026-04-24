package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

    public WokRuntimeService(EmakiCookingPlugin plugin,
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
        for (Map.Entry<StationCoordinates, emaki.jiuwu.craft.corelib.yaml.YamlSection> entry : stateStore.loadAll(StationType.WOK).entrySet()) {
            StationCoordinates coordinates = entry.getKey();
            WokState state = readState(entry.getValue());
            Block block = coordinates.block();
            if (state == null || block == null || !blockMatcher.matches(block, StationType.WOK) || !state.hasIngredients()) {
                stateStore.delete(coordinates);
            }
        }
    }

    public boolean handleInteraction(StationInteraction interaction) {
        Block block = interaction.block();
        Player player = interaction.player();
        if (block == null || player == null || !interaction.mainHand() || !blockMatcher.matches(block, StationType.WOK)) {
            return false;
        }
        if (settingsService.requireSneaking(StationType.WOK) && !player.isSneaking()) {
            return false;
        }
        if (!player.hasPermission("emakicooking.station.wok.use")
                && !player.hasPermission("emakicooking.admin")) {
            messageService.send(player, "general.no_permission");
            interaction.cancel();
            return true;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        WokState state = readState(stateStore.load(coordinates));
        int heatLevel = resolveHeatLevel(block.getRelative(BlockFace.DOWN));
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (interaction.rightClick()) {
            if (!isSpatula(hand)) {
                return false;
            }
            if (state == null || !state.hasIngredients()) {
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.no_item", Map.of());
                interaction.cancel();
                return true;
            }
            showContents(player, state, heatLevel);
            interaction.cancel();
            return true;
        }

        if (!interaction.leftClick()) {
            return false;
        }

        if (isSpatula(hand)) {
            if (!player.hasPermission("emakicooking.station.wok.stir")
                    && !player.hasPermission("emakicooking.admin")) {
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
                        ingredient.stirTimes() + 1
                ));
            }
            WokState updated = new WokState(updatedIngredients, state.totalStirCount() + 1, now, now);
            saveState(coordinates, updated);
            Location particleLocation = block.getLocation().add(0.5D, 1.05D, 0.5D);
            if (particleLocation.getWorld() != null) {
                particleLocation.getWorld().spawnParticle(Particle.CLOUD, particleLocation, 4, 0.15D, 0.1D, 0.15D, 0.01D);
            }
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.stir_count", Map.of("count", updated.totalStirCount()));
            interaction.cancel();
            return true;
        }

        boolean servingWithBowl = settingsService.wokNeedBowl() && isPlainBowl(hand);
        boolean servingWithEmptyHand = !settingsService.wokNeedBowl() && (hand == null || hand.getType().isAir());
        if (servingWithBowl || servingWithEmptyHand) {
            if (!player.hasPermission("emakicooking.station.wok.serve")
                    && !player.hasPermission("emakicooking.admin")) {
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
                ItemStack consumed = CookingRuntimeUtil.takeOneFromMainHand(player);
                if (consumed == null || consumed.getType().isAir()) {
                    return false;
                }
                WokState created = new WokState(List.of(new WokIngredientState(source, 1, 0)), 0, 0L, 0L);
                saveState(coordinates, created);
                CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.ingredient_added", Map.of("item", itemDisplayName(source)));
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
                        last.stirTimes()
                ));
            } else {
                updatedIngredients.add(new WokIngredientState(source, 1, 0));
            }
            saveState(coordinates, new WokState(updatedIngredients, state.totalStirCount(), state.lastStirTimeMs(), state.lastStirActionMs()));
            CookingRuntimeUtil.sendActionBar(plugin, player, messageService, "wok.ingredient_added", Map.of("item", itemDisplayName(source)));
            interaction.cancel();
            return true;
        }

        if (state == null || !state.hasIngredients()) {
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
        if (block == null || !blockMatcher.matches(block, StationType.WOK)) {
            return false;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        WokState state = readState(stateStore.load(coordinates));
        if (state == null || !state.hasIngredients()) {
            return false;
        }
        Location dropLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (WokIngredientState ingredient : state.ingredients()) {
            ItemSource source = ItemSourceUtil.parse(ingredient.source());
            ItemStack itemStack = source == null ? null : itemSourceService.createItem(source, ingredient.amount());
            if (itemStack != null && !itemStack.getType().isAir() && dropLocation.getWorld() != null) {
                dropLocation.getWorld().dropItemNaturally(dropLocation, itemStack);
            }
        }
        stateStore.delete(coordinates);
        return true;
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
        return true;
    }

    private Map<String, Object> invalidOutcome(RecipeDocument recipe) {
        Map<String, Object> configured = recipeService.outcome(recipe, "result.invalid");
        if (!configured.isEmpty()) {
            return configured;
        }
        if (Texts.isNotBlank(settingsService.wokInvalidResultSource())) {
            return Map.of("source", settingsService.wokInvalidResultSource(), "amount", 1);
        }
        if (Texts.isNotBlank(settingsService.wokFailureOutputSource())) {
            return Map.of("source", settingsService.wokFailureOutputSource(), "amount", 1);
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
                List.of(Map.of("source", source, "amount", 1)),
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
            String permission = recipe.configuration().getString("permission", "");
            if (Texts.isNotBlank(permission) && player != null && !player.hasPermission(permission)) {
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
                String expectedSource = String.valueOf(ingredient.getOrDefault("source", ""));
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
        ItemSource source = ItemSourceUtil.parse(last.source());
        ItemStack itemStack = source == null ? null : itemSourceService.createItem(source, 1);
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        InventoryItemUtil.giveOrDrop(player, itemStack);
        if (last.amount() <= 1) {
            updatedIngredients.remove(lastIndex);
        } else {
            updatedIngredients.set(lastIndex, new WokIngredientState(last.source(), last.amount() - 1, last.stirTimes()));
        }
        if (updatedIngredients.isEmpty()) {
            clearState(coordinates);
        } else {
            saveState(coordinates, new WokState(updatedIngredients, state.totalStirCount(), state.lastStirTimeMs(), state.lastStirActionMs()));
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
            if (blockMatcher.matches(block, rule.source())) {
                resolved = Math.max(resolved, rule.level());
            }
        }
        return resolved;
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
        String displayName = itemSourceService.displayName(ItemSourceUtil.parse(source));
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
        stateStore.save(coordinates, root);
    }

    private void clearState(StationCoordinates coordinates) {
        stateStore.delete(coordinates);
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
                    CookingRuntimeUtil.parseInteger(normalized.get("stir_times"), 0)
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

    private record WokIngredientState(String source, int amount, int stirTimes) {
    }
}
