package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class OvenTickProcessor {

    private final CookingSettingsService settingsService;
    private final CookingRecipeService recipeService;
    private final CookingRewardService rewardService;
    private final ItemSourceService itemSourceService;
    private final OvenStateCodec codec;
    private CookingCompletionCoordinator completionCoordinator;

    OvenTickProcessor(CookingSettingsService settingsService,
            CookingRecipeService recipeService,
            CookingRewardService rewardService,
            ItemSourceService itemSourceService,
            OvenStateCodec codec) {
        this.settingsService = settingsService;
        this.recipeService = recipeService;
        this.rewardService = rewardService;
        this.itemSourceService = itemSourceService;
        this.codec = codec;
    }

    void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        this.completionCoordinator = completionCoordinator;
    }

    boolean processStation(StationCoordinates coordinates,
            OvenState state,
            Block block,
            long now) {
        boolean changed = false;
        if (state.burningUntilMs() > 0L && now >= state.burningUntilMs()) {
            state.setBurningUntilMs(0L);
            state.setHeat(0);
            changed = true;
        }
        if (state.burningUntilMs() > now && state.heat() > 0 && settingsService.ovenHeatDecayPerSecond() > 0) {
            int newHeat = Math.max(0, state.heat() - settingsService.ovenHeatDecayPerSecond());
            if (newHeat != state.heat()) {
                state.setHeat(newHeat);
                changed = true;
            }
        }
        if (state.burningUntilMs() > now && heatInNormalRange(state) && processCooking(coordinates, block, state)) {
            changed = true;
        }
        if (!state.hasSlots() && state.burningUntilMs() > 0L) {
            state.setBurningUntilMs(0L);
            state.setHeat(0);
            changed = true;
        }
        return changed;
    }

    boolean processCooking(StationCoordinates coordinates, Block ovenBlock, OvenState state) {
        if (ovenBlock == null || state == null) {
            return false;
        }
        boolean changed = false;
        List<Integer> validSlots = new ArrayList<>();
        Map<Integer, RecipeDocument> recipesBySlot = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            RecipeDocument recipe = recipeService.findOvenRecipe(entry.getValue(), null);
            if (recipe != null) {
                validSlots.add(entry.getKey());
                recipesBySlot.put(entry.getKey(), recipe);
            }
        }
        for (Integer slot : validSlots) {
            RecipeDocument recipe = recipesBySlot.get(slot);
            int requiredSeconds = recipeService.ovenBakeTimeSeconds(recipe);
            int progress = state.progressAt(slot) + 1;
            if (heatInPerfectRange(state, recipe)) {
                state.setPerfectProgress(slot, state.perfectProgressAt(slot) + 1);
            }
            if (progress >= requiredSeconds) {
                if (completeSlot(coordinates, ovenBlock, state, slot, recipe, determineStage(state, slot, recipe, requiredSeconds))) {
                    return true;
                }
            } else {
                state.setProgress(slot, progress);
            }
            changed = true;
        }
        return changed;
    }

    boolean completeSlot(StationCoordinates coordinates, Block ovenBlock, OvenState state, int slot, RecipeDocument recipe) {
        return completeSlot(coordinates, ovenBlock, state, slot, recipe, OvenBakeStage.NORMAL);
    }

    boolean completeSlot(StationCoordinates coordinates, Block ovenBlock, OvenState state, int slot, RecipeDocument recipe, OvenBakeStage stage) {
        if (coordinates == null || ovenBlock == null || state == null || recipe == null) {
            return false;
        }
        Map<String, Object> outcome = recipeService.ovenOutcomeForStage(recipe, stage);
        List<Map<String, Object>> outputs = recipeService.outputs(outcome);
        List<String> actions = combineActions(recipeService.actions(recipe), recipeService.actions(outcome));
        Location rewardLocation = ovenBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        Player player = state.playerUuid() == null ? null : Bukkit.getPlayer(state.playerUuid());
        List<CookingInputIngredient> inputs = List.of(new CookingInputIngredient(state.slotSources().get(slot), 1));
        Map<String, Object> placeholders = Map.of(
                "recipe_id", recipe.id(),
                "station_type", StationType.OVEN.folderName(),
                "slot_index", slot,
                "stage", stage.name().toLowerCase(java.util.Locale.ROOT)
        );

        OvenState committed = copyState(coordinates, state);
        List<Map<String, Object>> committedOutputs = outputs;
        boolean dropResult = settingsService.ovenDropResult();
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
                        "cooking_oven_complete",
                        placeholders
                );
                if (storedItem != null && !storedItem.getType().isAir()) {
                    committed.setSlotSource(slot, source);
                    committed.setSlotItem(slot, codec.serializeItem(storedItem));
                    committed.setProgress(slot, 0);
                    committed.setPerfectProgress(slot, 0);
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
                "oven:" + slot + ":" + state.progressAt(slot) + ":" + stage.name().toLowerCase(java.util.Locale.ROOT),
                StationType.OVEN,
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
                "cooking_oven_complete",
                placeholders,
                List.of(),
                // No player inventory input on this path; the pipeline evaluates the condition itself.
                null
        ));
        return accepted;
    }

    private OvenState copyState(StationCoordinates coordinates, OvenState state) {
        return codec.readState(new emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection(codec.serializeState(coordinates, state)));
    }

    private OvenBakeStage determineStage(OvenState state, int slot, RecipeDocument recipe, int requiredSeconds) {
        if (requiredSeconds <= 0) {
            return OvenBakeStage.NORMAL;
        }
        if (recipeService.ovenOverbakeSeconds(recipe) > 0 && state.heat() > recipeService.ovenPerfectHeatMax(recipe)) {
            return OvenBakeStage.OVERBAKED;
        }
        double ratio = (double) state.perfectProgressAt(slot) / (double) requiredSeconds;
        return ratio >= recipeService.ovenPerfectRequiredRatio(recipe) ? OvenBakeStage.PERFECT : OvenBakeStage.NORMAL;
    }

    private boolean heatInPerfectRange(OvenState state, RecipeDocument recipe) {
        if (state == null || recipe == null) {
            return false;
        }
        int heat = state.heat();
        return heat >= recipeService.ovenPerfectHeatMin(recipe) && heat <= recipeService.ovenPerfectHeatMax(recipe);
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
        ItemSourceRef source = ItemSourceUtil.parse(output.get("item_sources"));
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

    boolean shouldRemainActive(OvenState state, long now) {
        return state != null && (state.burningUntilMs() > now || state.heat() > 0 || state.hasSlots());
    }

    boolean hasValidIngredients(OvenState state) {
        if (state == null || state.slotSources().isEmpty()) {
            return false;
        }
        for (String source : state.slotSources().values()) {
            if (recipeService.findOvenRecipe(source, null) != null) {
                return true;
            }
        }
        return false;
    }

    boolean heatInNormalRange(OvenState state) {
        if (state == null) {
            return false;
        }
        int heat = state.heat();
        return heat >= settingsService.ovenHeatMin() && heat <= settingsService.ovenHeatMax();
    }

    void dropStoredItems(Block ovenBlock, OvenState state) {
        if (ovenBlock == null || state == null || ovenBlock.getWorld() == null) {
            return;
        }
        Location dropLocation = ovenBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Map.Entry<Integer, String> entry : codec.sortedSlots(state.slotSources()).entrySet()) {
            ItemStack storedItem = codec.deserializeItem(state.slotItemData(entry.getKey()));
            if (storedItem != null && !storedItem.getType().isAir() && !isCompleted(entry.getKey(), entry.getValue(), state)) {
                ovenBlock.getWorld().dropItemNaturally(dropLocation, storedItem);
                continue;
            }
            String dropSource = entry.getValue();
            RecipeDocument recipe = recipeService.findOvenRecipe(dropSource, null);
            if (recipe != null && state.progressAt(entry.getKey()) >= recipeService.ovenBakeTimeSeconds(recipe)) {
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
                ovenBlock.getWorld().dropItemNaturally(dropLocation, itemStack);
            }
        }
    }

    private boolean isCompleted(int slot, String source, OvenState state) {
        RecipeDocument recipe = recipeService.findOvenRecipe(source, null);
        return recipe != null && state.progressAt(slot) >= recipeService.ovenBakeTimeSeconds(recipe);
    }
}
