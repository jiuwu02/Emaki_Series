package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeMaterialUsagePlanner {

    private final EmakiForgePlugin plugin;

    ForgeMaterialUsagePlanner(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    List<ForgeMaterialContribution> collectMaterialContributions(Recipe recipe, GuiItems guiItems) {
        List<ForgeMaterialContribution> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        int sequence = 0;
        sequence = appendRequiredContributions(result, recipe, guiItems, sequence);
        appendOptionalContributions(result, recipe, guiItems, sequence);
        return result;
    }

    int optionalCapacityCost(Recipe recipe, GuiItems guiItems) {
        return sumOptionalContributions(recipe, guiItems, ForgeMaterial::effectiveCapacityCost);
    }

    int optionalCapacityBonus(Recipe recipe, GuiItems guiItems) {
        return sumOptionalContributions(recipe, guiItems, ForgeMaterial::forgeCapacityBonus);
    }

    private int sumOptionalContributions(Recipe recipe, GuiItems guiItems, ToIntFunction<ForgeMaterial> extractor) {
        int total = 0;
        for (ForgeMaterialContribution c : collectOptionalContributions(recipe, guiItems, 0)) {
            total += extractor.applyAsInt(c.material()) * c.amount();
        }
        return total;
    }

    List<ItemStack> unconsumedInputs(Recipe recipe, GuiItems guiItems) {
        List<ItemStack> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        appendUnconsumedItems(
                result,
                guiItems.blueprints(),
                source -> blueprintKey(recipe, source),
                blueprintConsumption(recipe)
        );
        appendUnconsumedItems(
                result,
                guiItems.requiredMaterials(),
                source -> materialKey(recipe, source, false),
                requiredMaterialConsumption(recipe)
        );
        appendUnconsumedItems(
                result,
                guiItems.optionalMaterials(),
                source -> materialKey(recipe, source, true),
                optionalMaterialConsumption(recipe, guiItems)
        );
        return result;
    }

    private int appendRequiredContributions(List<ForgeMaterialContribution> result,
            Recipe recipe,
            GuiItems guiItems,
            int sequence) {
        List<InputStack> inputs = inputStacks(guiItems.requiredMaterials());
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
            Recipe recipe,
            GuiItems guiItems,
            int sequence) {
        int nextSequence = sequence;
        for (ForgeMaterialContribution contribution : collectOptionalContributions(recipe, guiItems, sequence)) {
            result.add(contribution);
            nextSequence = Math.max(nextSequence, contribution.sequence() + 1);
        }
        return nextSequence;
    }

    private List<ForgeMaterialContribution> collectOptionalContributions(Recipe recipe, GuiItems guiItems, int sequence) {
        List<ForgeMaterialContribution> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        List<InputStack> inputs = inputStacks(guiItems.optionalMaterials());
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

    private Map<String, Integer> optionalMaterialConsumption(Recipe recipe, GuiItems guiItems) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        List<InputStack> inputs = inputStacks(guiItems.optionalMaterials());
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
            Map<Integer, ItemStack> inputs,
            InputKeyResolver keyResolver,
            Map<String, Integer> remainingConsumption) {
        if (result == null || inputs == null || inputs.isEmpty()) {
            return;
        }
        Map<String, Integer> consumption = new LinkedHashMap<>(remainingConsumption == null ? Map.of() : remainingConsumption);
        for (InputStack input : inputStacks(inputs)) {
            ItemStack itemStack = input.itemStack();
            if (itemStack == null || isEmpty(itemStack)) {
                continue;
            }
            String key = keyResolver == null ? "" : keyResolver.resolve(input.source());
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

    private List<InputStack> inputStacks(Map<Integer, ItemStack> inputs) {
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
            result.add(new InputStack(
                    entry.getKey() == null ? -1 : entry.getKey(),
                    itemStack,
                    identify(itemStack)
            ));
        }
        return result;
    }

    private InputStack firstMatchingInput(List<InputStack> inputs, ForgeMaterial material) {
        if (inputs == null || material == null) {
            return null;
        }
        for (InputStack input : inputs) {
            if (input != null && material.matches(input.source())) {
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
            if (input == null || !material.matches(input.source())) {
                continue;
            }
            total += input.itemStack().getAmount();
        }
        return total;
    }

    private String blueprintKey(Recipe recipe, ItemSource source) {
        if (recipe == null || source == null) {
            return "";
        }
        for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if (requirement != null && requirement.matches(source)) {
                return requirement.key();
            }
        }
        return "";
    }

    private String materialKey(Recipe recipe, ItemSource source, boolean optional) {
        ForgeMaterial material = recipe == null || source == null ? null : recipe.findMaterialBySource(source, optional);
        return material == null ? "" : material.key();
    }

    private ItemSource identify(ItemStack itemStack) {
        return plugin == null || plugin.itemIdentifierService() == null || isEmpty(itemStack)
                ? null
                : plugin.itemIdentifierService().identifyItem(itemStack);
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

        String resolve(ItemSource source);
    }

    private record InputStack(int slot, ItemStack itemStack, ItemSource source) {

    }
}
