package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeMaterialUsagePlanner {

    private final EmakiForgePlugin plugin;
    private final ItemIdentifierService itemIdentifierService;

    ForgeMaterialUsagePlanner(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.itemIdentifierService = null;
    }

    ForgeMaterialUsagePlanner(ItemIdentifierService itemIdentifierService) {
        this.plugin = null;
        this.itemIdentifierService = itemIdentifierService;
    }

    List<ForgeMaterialContribution> collectMaterialContributions(Player player, Recipe recipe, GuiItems guiItems) {
        List<ForgeMaterialContribution> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        int sequence = 0;
        sequence = appendRequiredContributions(result, player, recipe, guiItems, sequence);
        appendOptionalContributions(result, player, recipe, guiItems, sequence);
        return result;
    }

    Map<String, Integer> placedAmounts(Player player, Recipe recipe, GuiItems guiItems) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        List<InputStack> requiredInputs = inputStacks(player, guiItems.requiredMaterials());
        List<InputStack> optionalInputs = inputStacks(player, guiItems.optionalMaterials());
        for (ForgeMaterial material : recipe.materials()) {
            if (material == null || isBlank(material.key())) {
                continue;
            }
            List<InputStack> inputs = material.optional() ? optionalInputs : requiredInputs;
            result.merge(material.key(), totalAmount(inputs, material), Integer::sum);
        }
        return result;
    }

    int optionalCapacityCost(Player player, Recipe recipe, GuiItems guiItems) {
        return sumOptionalContributions(player, recipe, guiItems, ForgeMaterial::effectiveCapacityCost);
    }

    int optionalCapacityBonus(Player player, Recipe recipe, GuiItems guiItems) {
        return sumOptionalContributions(player, recipe, guiItems, ForgeMaterial::forgeCapacityBonus);
    }

    private int sumOptionalContributions(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ToIntFunction<ForgeMaterial> extractor) {
        int total = 0;
        for (ForgeMaterialContribution c : collectOptionalContributions(player, recipe, guiItems, 0)) {
            total += extractor.applyAsInt(c.material()) * c.amount();
        }
        return total;
    }

    List<ItemStack> unconsumedInputs(Player player, Recipe recipe, GuiItems guiItems) {
        List<ItemStack> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        appendUnconsumedItems(
                result,
                player,
                guiItems.blueprints(),
                context -> blueprintKey(recipe, context),
                blueprintConsumption(recipe)
        );
        appendUnconsumedItems(
                result,
                player,
                guiItems.requiredMaterials(),
                context -> materialKey(recipe, context, false),
                requiredMaterialConsumption(recipe)
        );
        appendUnconsumedItems(
                result,
                player,
                guiItems.optionalMaterials(),
                context -> materialKey(recipe, context, true),
                optionalMaterialConsumption(player, recipe, guiItems)
        );
        return result;
    }

    private int appendRequiredContributions(List<ForgeMaterialContribution> result,
            Player player,
            Recipe recipe,
            GuiItems guiItems,
            int sequence) {
        List<InputStack> inputs = inputStacks(player, guiItems.requiredMaterials());
        int nextSequence = sequence;
        for (ForgeMaterial material : recipe.requiredMaterials()) {
            InputStack firstInput = firstMatchingInput(inputs, material);
            if (firstInput == null || totalAmount(inputs, material) < unitAmount(material)) {
                continue;
            }
            result.add(new ForgeMaterialContribution(
                    material,
                    1,
                    firstInput.slot(),
                    "required",
                    nextSequence++,
                    firstInput.source()
            ));
        }
        return nextSequence;
    }

    private int appendOptionalContributions(List<ForgeMaterialContribution> result,
            Player player,
            Recipe recipe,
            GuiItems guiItems,
            int sequence) {
        int nextSequence = sequence;
        for (ForgeMaterialContribution contribution : collectOptionalContributions(player, recipe, guiItems, sequence)) {
            result.add(contribution);
            nextSequence = Math.max(nextSequence, contribution.sequence() + 1);
        }
        return nextSequence;
    }

    private List<ForgeMaterialContribution> collectOptionalContributions(Player player,
            Recipe recipe,
            GuiItems guiItems,
            int sequence) {
        List<ForgeMaterialContribution> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        List<InputStack> inputs = inputStacks(player, guiItems.optionalMaterials());
        int nextSequence = sequence;
        for (ForgeMaterial material : recipe.optionalMaterials()) {
            InputStack firstInput = firstMatchingInput(inputs, material);
            if (firstInput == null) {
                continue;
            }
            int batches = totalAmount(inputs, material) / unitAmount(material);
            if (batches <= 0) {
                continue;
            }
            result.add(new ForgeMaterialContribution(
                    material,
                    batches,
                    firstInput.slot(),
                    "optional",
                    nextSequence++,
                    firstInput.source()
            ));
        }
        return result;
    }

    private Map<String, Integer> blueprintConsumption(Recipe recipe) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (recipe == null) {
            return result;
        }
        for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if (requirement == null || isBlank(requirement.key())) {
                continue;
            }
            result.merge(requirement.key(), Math.max(1, requirement.amount()), Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> requiredMaterialConsumption(Recipe recipe) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (recipe == null) {
            return result;
        }
        for (ForgeMaterial material : recipe.requiredMaterials()) {
            if (material == null || isBlank(material.key())) {
                continue;
            }
            result.merge(material.key(), unitAmount(material), Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> optionalMaterialConsumption(Player player, Recipe recipe, GuiItems guiItems) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        List<InputStack> inputs = inputStacks(player, guiItems.optionalMaterials());
        for (ForgeMaterial material : recipe.optionalMaterials()) {
            if (material == null || isBlank(material.key())) {
                continue;
            }
            int unitAmount = unitAmount(material);
            int amountToConsume = (totalAmount(inputs, material) / unitAmount) * unitAmount;
            if (amountToConsume > 0) {
                result.merge(material.key(), amountToConsume, Integer::sum);
            }
        }
        return result;
    }

    private void appendUnconsumedItems(List<ItemStack> result,
            Player player,
            Map<Integer, ItemStack> inputs,
            InputKeyResolver keyResolver,
            Map<String, Integer> remainingConsumption) {
        if (result == null || inputs == null || inputs.isEmpty()) {
            return;
        }
        Map<String, Integer> consumption = new LinkedHashMap<>(remainingConsumption == null ? Map.of() : remainingConsumption);
        for (InputStack input : inputStacks(player, inputs)) {
            ItemStack itemStack = input.itemStack();
            if (itemStack == null || isEmpty(itemStack)) {
                continue;
            }
            String key = keyResolver == null ? "" : keyResolver.resolve(input.context());
            int consume = isBlank(key) ? 0 : Math.min(itemStack.getAmount(), consumption.getOrDefault(key, 0));
            if (consume > 0) {
                consumption.computeIfPresent(key, (_, current) -> Math.max(0, current - consume));
            }
            int unconsumed = Math.max(0, itemStack.getAmount() - consume);
            if (unconsumed <= 0) {
                continue;
            }
            ItemStack clone = itemStack.clone();
            clone.setAmount(unconsumed);
            result.add(clone);
        }
    }

    private List<InputStack> inputStacks(Player player, Map<Integer, ItemStack> inputs) {
        List<InputStack> result = new ArrayList<>();
        if (inputs == null || inputs.isEmpty()) {
            return result;
        }
        List<Map.Entry<Integer, ItemStack>> entries = new ArrayList<>(inputs.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, ItemStack> entry : entries) {
            ItemStack itemStack = entry.getValue();
            if (isEmpty(itemStack)) {
                continue;
            }
            ItemSourceRef source = identify(itemStack);
            result.add(new InputStack(
                    entry.getKey() == null ? -1 : entry.getKey(),
                    itemStack,
                    source,
                    MatchContext.of(itemStack, source, player)
            ));
        }
        return result;
    }

    private InputStack firstMatchingInput(List<InputStack> inputs, ForgeMaterial material) {
        if (inputs == null || material == null) {
            return null;
        }
        for (InputStack input : inputs) {
            if (input != null && material.matches(input.context())) {
                return input;
            }
        }
        return null;
    }

    private int totalAmount(List<InputStack> inputs, ForgeMaterial material) {
        if (inputs == null || material == null) {
            return 0;
        }
        int total = 0;
        for (InputStack input : inputs) {
            if (input == null || !material.matches(input.context())) {
                continue;
            }
            total += input.itemStack().getAmount();
        }
        return total;
    }

    private String blueprintKey(Recipe recipe, MatchContext context) {
        if (recipe == null || context == null) {
            return "";
        }
        BlueprintRequirement requirement = recipe.findBlueprintRequirementMatching(context);
        return requirement == null ? "" : requirement.key();
    }

    private String materialKey(Recipe recipe, MatchContext context, boolean optional) {
        ForgeMaterial material = recipe == null ? null : recipe.findMaterialMatching(context, optional);
        return material == null ? "" : material.key();
    }

    private ItemSourceRef identify(ItemStack itemStack) {
        ItemIdentifierService identifier = itemIdentifierService != null
                ? itemIdentifierService
                : plugin == null ? null : plugin.itemIdentifierService();
        return identifier == null || isEmpty(itemStack) ? null : identifier.identifyItem(itemStack);
    }

    private int unitAmount(ForgeMaterial material) {
        return material == null ? 1 : Math.max(1, material.amount());
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface InputKeyResolver {

        String resolve(MatchContext context);
    }

    private record InputStack(int slot, ItemStack itemStack, ItemSourceRef source, MatchContext context) {

    }
}
