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
        int sequence = appendContributions(result,
                assignMaterials(player, recipe, guiItems.requiredMaterials(), false), false, 0);
        appendContributions(result,
                assignMaterials(player, recipe, guiItems.optionalMaterials(), true), true, sequence);
        return result;
    }

    Map<String, Integer> placedAmounts(Player player, Recipe recipe, GuiItems guiItems) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        appendPlaced(result, assignMaterials(player, recipe, guiItems.requiredMaterials(), false));
        appendPlaced(result, assignMaterials(player, recipe, guiItems.optionalMaterials(), true));
        return result;
    }

    int optionalCapacityCost(Player player, Recipe recipe, GuiItems guiItems) {
        return sumOptionalContributions(player, recipe, guiItems, ForgeMaterial::effectiveCapacityCost);
    }

    int optionalCapacityBonus(Player player, Recipe recipe, GuiItems guiItems) {
        return sumOptionalContributions(player, recipe, guiItems, ForgeMaterial::forgeCapacityBonus);
    }

    private int sumOptionalContributions(Player player, Recipe recipe, GuiItems guiItems,
            ToIntFunction<ForgeMaterial> extractor) {
        int total = 0;
        if (recipe == null || guiItems == null) {
            return total;
        }
        List<ForgeMaterialContribution> contributions = new ArrayList<>();
        appendContributions(contributions,
                assignMaterials(player, recipe, guiItems.optionalMaterials(), true), true, 0);
        for (ForgeMaterialContribution contribution : contributions) {
            total += extractor.applyAsInt(contribution.material()) * contribution.amount();
        }
        return total;
    }

    List<ItemStack> unconsumedInputs(Player player, Recipe recipe, GuiItems guiItems) {
        List<ItemStack> result = new ArrayList<>();
        if (recipe == null || guiItems == null) {
            return result;
        }
        appendUnconsumedBlueprints(result,
                assignBlueprints(player, recipe, guiItems.blueprints()),
                blueprintConsumption(recipe));
        appendUnconsumedMaterials(result,
                assignMaterials(player, recipe, guiItems.requiredMaterials(), false),
                materialConsumption(recipe.requiredMaterials(), null));
        List<AssignedMaterialInput> optionalInputs = assignMaterials(player, recipe, guiItems.optionalMaterials(), true);
        appendUnconsumedMaterials(result, optionalInputs,
                materialConsumption(recipe.optionalMaterials(), optionalInputs));
        return result;
    }

    private int appendContributions(List<ForgeMaterialContribution> result,
            List<AssignedMaterialInput> inputs,
            boolean optional,
            int sequence) {
        Map<String, List<AssignedMaterialInput>> groups = new LinkedHashMap<>();
        for (AssignedMaterialInput input : inputs) {
            groups.computeIfAbsent(input.material().materialId(), ignored -> new ArrayList<>()).add(input);
        }
        int nextSequence = sequence;
        for (List<AssignedMaterialInput> group : groups.values()) {
            if (group.isEmpty()) {
                continue;
            }
            AssignedMaterialInput first = group.get(0);
            int unit = unitAmount(first.material());
            int total = group.stream().mapToInt(input -> input.itemStack().getAmount()).sum();
            int batches = optional ? total / unit : total >= unit ? 1 : 0;
            if (batches > 0) {
                result.add(new ForgeMaterialContribution(first.material(), batches, first.slot(),
                        optional ? "optional" : "required", nextSequence++, first.source()));
            }
        }
        return nextSequence;
    }

    private Map<String, Integer> materialUnits(List<ForgeMaterial> materials) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (materials == null) {
            return result;
        }
        for (ForgeMaterial material : materials) {
            if (material == null || isBlank(material.countKey())) {
                continue;
            }
            result.merge(material.countKey(), unitAmount(material), Math::max);
        }
        return result;
    }

    private Map<String, Integer> blueprintConsumption(Recipe recipe) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (recipe == null) {
            return result;
        }
        for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if (requirement != null && !isBlank(requirement.blueprintId())) {
                result.merge(requirement.blueprintId(), Math.max(1, requirement.amount()), Math::max);
            }
        }
        return result;
    }

    private Map<String, Integer> materialConsumption(List<ForgeMaterial> materials,
            List<AssignedMaterialInput> inputs) {
        Map<String, Integer> units = materialUnits(materials);
        if (inputs == null) {
            return ForgeAllocationMath.requiredConsumption(units);
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        appendPlaced(totals, inputs);
        return ForgeAllocationMath.optionalConsumption(units, totals);
    }

    private void appendPlaced(Map<String, Integer> result, List<AssignedMaterialInput> inputs) {
        for (AssignedMaterialInput input : inputs) {
            result.merge(input.material().countKey(), input.itemStack().getAmount(), Integer::sum);
        }
    }

    private void appendUnconsumedMaterials(List<ItemStack> result,
            List<AssignedMaterialInput> inputs,
            Map<String, Integer> remaining) {
        Map<String, Integer> consumption = new LinkedHashMap<>(remaining);
        for (AssignedMaterialInput input : inputs) {
            appendUnconsumed(result, input.itemStack(), input.material().countKey(), consumption);
        }
    }

    private void appendUnconsumedBlueprints(List<ItemStack> result,
            List<AssignedBlueprintInput> inputs,
            Map<String, Integer> remaining) {
        Map<String, Integer> consumption = new LinkedHashMap<>(remaining);
        for (AssignedBlueprintInput input : inputs) {
            appendUnconsumed(result, input.itemStack(), input.requirement().blueprintId(), consumption);
        }
    }

    private void appendUnconsumed(List<ItemStack> result, ItemStack itemStack, String key,
            Map<String, Integer> consumption) {
        int consume = Math.min(itemStack.getAmount(), consumption.getOrDefault(key, 0));
        if (consume > 0) {
            consumption.put(key, Math.max(0, consumption.getOrDefault(key, 0) - consume));
        }
        int unconsumed = Math.max(0, itemStack.getAmount() - consume);
        if (unconsumed > 0) {
            ItemStack clone = itemStack.clone();
            clone.setAmount(unconsumed);
            result.add(clone);
        }
    }

    private List<AssignedMaterialInput> assignMaterials(Player player, Recipe recipe,
            Map<Integer, ItemStack> inputs, boolean optional) {
        List<AssignedMaterialInput> result = new ArrayList<>();
        for (InputStack input : inputStacks(player, inputs)) {
            ForgeMaterial material = recipe.findMaterialMatching(input.context(), optional);
            if (material != null) {
                result.add(new AssignedMaterialInput(input.slot(), input.itemStack(), input.source(), material));
            }
        }
        return result;
    }

    private List<AssignedBlueprintInput> assignBlueprints(Player player, Recipe recipe,
            Map<Integer, ItemStack> inputs) {
        List<AssignedBlueprintInput> result = new ArrayList<>();
        for (InputStack input : inputStacks(player, inputs)) {
            BlueprintRequirement requirement = recipe.findBlueprintRequirementMatching(input.context());
            if (requirement != null) {
                result.add(new AssignedBlueprintInput(input.slot(), input.itemStack(), requirement));
            }
        }
        return result;
    }

    private Map<String, List<AssignedMaterialInput>> groupMaterials(List<AssignedMaterialInput> inputs) {
        Map<String, List<AssignedMaterialInput>> result = new LinkedHashMap<>();
        for (AssignedMaterialInput input : inputs) {
            result.computeIfAbsent(input.material().countKey(), ignored -> new ArrayList<>()).add(input);
        }
        return result;
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
            result.add(new InputStack(entry.getKey() == null ? -1 : entry.getKey(), itemStack, source,
                    MatchContext.of(itemStack, source, player)));
        }
        return result;
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

    private record InputStack(int slot, ItemStack itemStack, ItemSourceRef source, MatchContext context) {
    }

    private record AssignedMaterialInput(int slot, ItemStack itemStack, ItemSourceRef source,
            ForgeMaterial material) {
    }

    private record AssignedBlueprintInput(int slot, ItemStack itemStack,
            BlueprintRequirement requirement) {
    }
}
