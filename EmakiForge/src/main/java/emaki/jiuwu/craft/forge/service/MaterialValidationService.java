package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;

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
import emaki.jiuwu.craft.forge.model.ValidationResult;

final class MaterialValidationService {

    private final EmakiForgePlugin plugin;
    private final ForgeMaterialUsagePlanner usagePlanner;

    MaterialValidationService(EmakiForgePlugin plugin, ForgeLookupIndex lookupIndex) {
        this.plugin = plugin;
        this.usagePlanner = new ForgeMaterialUsagePlanner(plugin);
    }

    ValidationResult validate(Player player, Recipe recipe, GuiItems guiItems) {
        if (recipe == null || guiItems == null) {
            return ValidationResult.fail("forge.error.no_recipe");
        }
        ValidationResult blueprintValidation = validateBlueprints(player, recipe, guiItems);
        if (!blueprintValidation.success()) {
            return blueprintValidation;
        }
        ValidationResult requiredValidation = validateRequiredMaterials(player, recipe, guiItems);
        if (!requiredValidation.success()) {
            return requiredValidation;
        }
        return validateOptionalMaterials(player, recipe, guiItems);
    }

    boolean acceptsMaterials(Player player, Recipe recipe, GuiItems guiItems) {
        if (recipe == null || guiItems == null) {
            return false;
        }
        return onlyContainsAllowedMaterials(player, recipe, guiItems.requiredMaterials(), false)
                && onlyContainsAllowedMaterials(player, recipe, guiItems.optionalMaterials(), true);
    }

    private ValidationResult validateBlueprints(Player player, Recipe recipe, GuiItems guiItems) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack itemStack : guiItems.blueprintList()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            ItemSourceRef source = identify(itemStack);
            if (source == null) {
                return ValidationResult.fail("blueprint.count_not_enough");
            }
            BlueprintRequirement matched = recipe.findBlueprintRequirementMatching(
                    matchContext(player, itemStack, source));
            if (matched == null) {
                continue;
            }
            counts.merge(matched.blueprintId(), itemStack.getAmount(), Integer::sum);
        }
        for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if (counts.getOrDefault(requirement.blueprintId(), 0) < requirement.amount()) {
                return ValidationResult.fail("blueprint.count_not_enough");
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateRequiredMaterials(Player player, Recipe recipe, GuiItems guiItems) {
        if (!onlyContainsAllowedMaterials(player, recipe, guiItems.requiredMaterials(), false)) {
            return ValidationResult.fail("forge.error.material_not_allowed");
        }
        Map<String, Integer> counts = countMaterials(player, recipe, guiItems.requiredMaterials(), false);
        for (ForgeMaterial material : recipe.requiredMaterials()) {
            if (counts.getOrDefault(material.countKey(), 0) < Math.max(1, material.amount())) {
                return ValidationResult.fail("material.count_not_enough");
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateOptionalMaterials(Player player, Recipe recipe, GuiItems guiItems) {
        if (!onlyContainsAllowedMaterials(player, recipe, guiItems.optionalMaterials(), true)) {
            return ValidationResult.fail("forge.error.material_not_allowed");
        }
        int occupiedStacks = 0;
        for (ItemStack itemStack : guiItems.optionalMaterials().values()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            occupiedStacks++;
            ForgeMaterial material = recipe.findMaterialMatching(
                    matchContext(player, itemStack, identify(itemStack)), true);
            if (material == null) {
                return ValidationResult.fail("forge.error.material_not_allowed");
            }
        }
        if (recipe.optionalMaterialLimit() > 0 && occupiedStacks > recipe.optionalMaterialLimit()) {
            return ValidationResult.fail("forge.error.material_count_exceeded", Map.of(
                    "current", occupiedStacks,
                    "max", recipe.optionalMaterialLimit()
            ));
        }
        int totalCapacity = usagePlanner.optionalCapacityCost(player, recipe, guiItems);
        int capacityBonus = usagePlanner.optionalCapacityBonus(player, recipe, guiItems);
        int maxCapacity = Math.max(0, recipe.forgeCapacity()) + capacityBonus;
        if (maxCapacity > 0 && totalCapacity > maxCapacity) {
            return ValidationResult.fail("forge.error.capacity_exceeded", Map.of(
                    "current", totalCapacity,
                    "max", maxCapacity
            ));
        }
        return ValidationResult.ok();
    }

    private boolean onlyContainsAllowedMaterials(Player player,
            Recipe recipe,
            Map<Integer, ItemStack> inputs,
            boolean optional) {
        for (ItemStack itemStack : inputs.values()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            ItemSourceRef source = identify(itemStack);
            if (recipe.findMaterialMatching(matchContext(player, itemStack, source), optional) == null) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Integer> countMaterials(Player player,
            Recipe recipe,
            Map<Integer, ItemStack> inputs,
            boolean optional) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack itemStack : inputs.values()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            ForgeMaterial material = recipe.findMaterialMatching(
                    matchContext(player, itemStack, identify(itemStack)), optional);
            String key = material == null ? "" : material.countKey();
            if (!key.isBlank()) {
                counts.merge(key, itemStack.getAmount(), Integer::sum);
            }
        }
        return counts;
    }

    private ItemSourceRef identify(ItemStack itemStack) {
        return plugin.itemIdentifierService() == null ? null : plugin.itemIdentifierService().identifyItem(itemStack);
    }

    private MatchContext matchContext(Player player, ItemStack itemStack, ItemSourceRef source) {
        return MatchContext.of(itemStack, source, player);
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }
}
