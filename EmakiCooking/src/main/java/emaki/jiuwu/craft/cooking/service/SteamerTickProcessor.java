package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
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
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;

final class SteamerTickProcessor {

    private final EmakiCookingPlugin plugin;
    private final CookingSettingsService settingsService;
    private final CookingBlockMatcher blockMatcher;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final SteamerStateCodec codec;
    private CookingCompletionCoordinator completionCoordinator;

    SteamerTickProcessor(EmakiCookingPlugin plugin,
            CookingSettingsService settingsService,
            CookingBlockMatcher blockMatcher,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            SteamerStateCodec codec) {
        this.plugin = plugin;
        this.settingsService = settingsService;
        this.blockMatcher = blockMatcher;
        this.recipeService = recipeService;
        this.rewardService = rewardService;
        this.itemSourceService = itemSourceService;
        this.codec = codec;
    }

    void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        this.completionCoordinator = completionCoordinator;
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
        if (processSteamConsumptionAndCooking(coordinates, block, state)) {
            changed = true;
        }
        return changed;
    }

    boolean processSteamConsumptionAndCooking(StationCoordinates coordinates, Block steamerBlock, SteamerState state) {
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
                if (completeSlot(coordinates, steamerBlock, state, slot, recipe)) {
                    return true;
                }
            } else {
                state.setProgress(slot, progress);
            }
            changed = true;
        }
        return changed;
    }

    boolean completeSlot(StationCoordinates coordinates, Block steamerBlock, SteamerState state, int slot, RecipeDocument recipe) {
        if (coordinates == null || steamerBlock == null || state == null || recipe == null) {
            return false;
        }
        Map<String, Object> outcome = recipeService.outcome(recipe, "result.success");
        List<Map<String, Object>> outputs = recipeService.outputs(outcome);
        List<String> actions = combineActions(recipeService.actions(recipe), recipeService.actions(outcome));
        Location rewardLocation = steamerBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        Player player = state.playerUuid() == null ? null : Bukkit.getPlayer(state.playerUuid());
        List<CookingInputIngredient> inputs = List.of(new CookingInputIngredient(state.slotSources().get(slot), 1));
        Map<String, Object> placeholders = Map.of(
                "recipe_id", recipe.id(),
                "station_type", StationType.STEAMER.folderName(),
                "slot_index", slot
        );

        SteamerState committed = copyState(coordinates, state);
        List<Map<String, Object>> committedOutputs = outputs;
        boolean dropResult = settingsService.steamerDropResult();
        boolean conditionBlocks = !rewardService.completionConditionPasses(recipe, player)
                && rewardService.completionConditionBlocksOutput(recipe);
        if (!conditionBlocks && !dropResult && canStoreOutcomeInSlot(outputs)) {
            Map<String, Object> storedOutput = outputs.getFirst();
            String source = outputSourceShorthand(storedOutput);
            if (Texts.isNotBlank(source)) {
                ItemStack storedItem = rewardService.createOutputItem(
                        recipe,
                        storedOutput,
                        player,
                        rewardLocation,
                        "cooking_steamer_complete",
                        placeholders
                );
                if (storedItem != null && !storedItem.getType().isAir()) {
                    committed.setSlotSource(slot, source);
                    committed.setSlotItem(slot, codec.serializeItem(storedItem));
                    committed.setProgress(slot, 0);
                    committedOutputs = List.of();
                    dropResult = false;
                } else {
                    committed.removeSlot(slot);
                }
            } else {
                committed.removeSlot(slot);
            }
        } else {
            committed.removeSlot(slot);
        }

        boolean emptyCommit = committed.isCompletelyEmpty();
        boolean accepted = completionCoordinator != null && completionCoordinator.submit(new CookingCompletionRequest(
                "steamer:" + slot + ":" + state.progressAt(slot) + ":" + state.steam(),
                StationType.STEAMER,
                coordinates,
                codec.serializeState(coordinates, state),
                emptyCommit ? CookingCompletionOperation.CommitMode.DELETE : CookingCompletionOperation.CommitMode.SAVE,
                emptyCommit ? Map.of() : codec.serializeState(coordinates, committed),
                recipe,
                player,
                rewardLocation,
                dropResult,
                inputs,
                committedOutputs,
                actions,
                "cooking_steamer_complete",
                placeholders,
                List.of(),

                null
        ));
        return accepted;
    }

    private SteamerState copyState(StationCoordinates coordinates, SteamerState state) {
        return codec.readState(new MapYamlSection(codec.serializeState(coordinates, state)));
    }

    boolean canStoreOutcomeInSlot(List<Map<String, Object>> outputs) {
        if (outputs == null || outputs.size() != 1) {
            return false;
        }
        Map<String, Object> output = outputs.getFirst();
        if (output == null || output.isEmpty()) {
            return false;
        }
        if (Texts.isBlank(outputSourceShorthand(output))) {
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

    private String outputSourceShorthand(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        ItemSourceRef source = CookingRuntimeUtil.parseOutputSource(plugin, output, "output");
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
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
        for (CookingSettingsService.HeatSourceIgnitionRule rule : settingsService.steamerHeatSourceIgnitionRules()) {
            if (matchesHeatSourceRule(block, rule)) {
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
        boolean directStateChanged = false;
        BlockData blockData = heatSourceBlock.getBlockData();
        if (blockData instanceof Lightable lightable) {
            lightable.setLit(true);
            heatSourceBlock.setBlockData(lightable);
            directStateChanged = true;
        }
        if (heatSourceBlock.getState() instanceof Furnace furnace) {
            long remainingTicks = Math.max(0L, (burningUntilMs - now) / 50L);
            furnace.setBurnTime((short) Math.min(Short.MAX_VALUE, remainingTicks));
            furnace.update();
            directStateChanged = true;
        }
        if (!directStateChanged && !blockMatcher.setCustomLit(heatSourceBlock, true)) {
            applyConfiguredHeatSourceTransition(heatSourceBlock, true);
        }
    }

    void extinguishHeatSource(Block heatSourceBlock) {
        if (heatSourceBlock == null) {
            return;
        }
        boolean directStateChanged = false;
        BlockData blockData = heatSourceBlock.getBlockData();
        if (blockData instanceof Lightable lightable) {
            lightable.setLit(false);
            heatSourceBlock.setBlockData(lightable);
            directStateChanged = true;
        }
        if (heatSourceBlock.getState() instanceof Furnace furnace) {
            furnace.setBurnTime((short) 0);
            furnace.update();
            directStateChanged = true;
        }
        if (!directStateChanged && !blockMatcher.setCustomLit(heatSourceBlock, false)) {
            applyConfiguredHeatSourceTransition(heatSourceBlock, false);
        }
    }

    private boolean matchesHeatSourceRule(Block block, CookingSettingsService.HeatSourceIgnitionRule rule) {
        return rule != null
                && (matchesSource(block, rule.source())
                || matchesSource(block, rule.litSource())
                || matchesSource(block, rule.unlitSource()));
    }

    private boolean applyConfiguredHeatSourceTransition(Block block, boolean lit) {
        if (block == null) {
            return false;
        }
        for (CookingSettingsService.HeatSourceIgnitionRule rule : settingsService.steamerHeatSourceIgnitionRules()) {
            if (rule == null) {
                continue;
            }
            ItemSourceRef target = lit ? rule.litSource() : rule.unlitSource();
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

    private boolean matchesSource(Block block, ItemSourceRef source) {
        return block != null && source != null && blockMatcher.matches(block, source);
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
                Map<String, Object> outcome = recipeService.outcome(recipe, "result.success");
                List<Map<String, Object>> outputs = recipeService.outputs(outcome);
                if (canStoreOutcomeInSlot(outputs)) {
                    String outputSource = outputSourceShorthand(outputs.getFirst());
                    dropSource = Texts.isBlank(outputSource) ? dropSource : outputSource;
                }
            }
            ItemSourceRef source = ItemSourceUtil.parse(dropSource);
            ItemStack itemStack = source == null ? null : itemSourceService.createItem(source, 1);
            if (itemStack != null && !itemStack.getType().isAir()) {
                steamerBlock.getWorld().dropItemNaturally(dropLocation, itemStack);
            }
        }
    }
}
