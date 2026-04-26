package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class SteamerTickProcessor {

    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final SteamerStateCodec codec;

    SteamerTickProcessor(CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            SteamerStateCodec codec) {
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.recipeService = recipeService;
        this.rewardService = rewardService;
        this.itemSourceService = itemSourceService;
        this.codec = codec;
    }

    boolean processStation(StationCoordinates coordinates,
            SteamerState state,
            Block block,
            long now) {
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
        return changed;
    }

    boolean processSteamConsumptionAndCooking(Block steamerBlock, SteamerState state) {
        if (steamerBlock == null || state == null) {
            return false;
        }
        int baseConsumption = settingsService.steamerSteamConsumptionEfficiency();
        int conversionEfficiency = settingsService.steamerSteamConversionEfficiency();
        int currentSteam = state.steam();
        boolean changed = false;

        List<Integer> validSlots = new ArrayList<>();
        Map<Integer, RecipeDocument> recipesBySlot = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
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

    void completeSlot(Block steamerBlock, SteamerState state, int slot, RecipeDocument recipe) {
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
                state.setSlotItem(slot, codec.serializeItem(storedItem));
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

    boolean canStoreOutcomeInSlot(List<Map<String, Object>> outputs) {
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

    List<String> combineActions(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return merged.isEmpty() ? List.of() : List.copyOf(merged);
    }

    boolean shouldRemainActive(SteamerState state, long now) {
        return state != null && (state.burningUntilMs() > now
                || state.moisture() > 0
                || state.steam() > 0
                || hasValidIngredients(state));
    }

    boolean hasValidIngredients(SteamerState state) {
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

    boolean isHeatSourceBlock(Block block) {
        if (block == null) {
            return false;
        }
        if (isBuiltinFurnaceHeatSource(block)) {
            return true;
        }
        for (ItemSource source : settingsService.steamerHeatSources()) {
            if (blockMatcher.matches(block, source)) {
                return true;
            }
        }
        return false;
    }

    boolean isBuiltinFurnaceHeatSource(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.FURNACE
                || type == Material.SMOKER
                || type == Material.BLAST_FURNACE;
    }

    void igniteHeatSource(Block heatSourceBlock, long burningUntilMs, long now) {
        if (heatSourceBlock == null) {
            return;
        }
        BlockData blockData = heatSourceBlock.getBlockData();
        if (blockData instanceof Lightable lightable) {
            lightable.setLit(true);
            heatSourceBlock.setBlockData(lightable);
        }
        if (heatSourceBlock.getState() instanceof Furnace furnace) {
            long remainingTicks = Math.max(0L, (burningUntilMs - now) / 50L);
            furnace.setBurnTime((short) Math.min(Short.MAX_VALUE, remainingTicks));
            furnace.update();
        }
    }

    void extinguishHeatSource(Block heatSourceBlock) {
        if (heatSourceBlock == null) {
            return;
        }
        BlockData blockData = heatSourceBlock.getBlockData();
        if (blockData instanceof Lightable lightable) {
            lightable.setLit(false);
            heatSourceBlock.setBlockData(lightable);
        }
        if (heatSourceBlock.getState() instanceof Furnace furnace) {
            furnace.setBurnTime((short) 0);
            furnace.update();
        }
    }

    void dropStoredItems(Block steamerBlock, SteamerState state) {
        if (steamerBlock == null || state == null || steamerBlock.getWorld() == null) {
            return;
        }
        Location dropLocation = steamerBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            ItemStack storedItem = codec.deserializeItem(state.slotItemData(entry.getKey()));
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
}
