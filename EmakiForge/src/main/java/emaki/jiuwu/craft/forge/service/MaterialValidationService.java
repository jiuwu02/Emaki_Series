package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
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

    ValidationResult validate(Recipe recipe, GuiItems guiItems) {
        if (recipe == null || guiItems == null) {
            return ValidationResult.fail("forge.error.no_recipe");
        }
        ValidationResult blueprintValidation = validateBlueprints(recipe, guiItems);
        if (!blueprintValidation.success()) {
            return blueprintValidation;
        }
        ValidationResult requiredValidation = validateRequiredMaterials(recipe, guiItems);
        if (!requiredValidation.success()) {
            return requiredValidation;
        }
        return validateOptionalMaterials(recipe, guiItems);
    }

    boolean acceptsMaterials(Recipe recipe, GuiItems guiItems) {
        if (recipe == null || guiItems == null) {
            return false;
        }
        return onlyContainsAllowedMaterials(recipe, guiItems.requiredMaterials(), false)
                && onlyContainsAllowedMaterials(recipe, guiItems.optionalMaterials(), true);
    }

    private ValidationResult validateBlueprints(Recipe recipe, GuiItems guiItems) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack itemStack : guiItems.blueprintList()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            ItemSource source = identify(itemStack);
            if (source == null) {
                return ValidationResult.fail("blueprint.count_not_enough");
            }
            String key = shorthand(source);
            counts.merge(key, itemStack.getAmount(), Integer::sum);
        }
        for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if (counts.getOrDefault(requirement.key(), 0) < requirement.amount()) {
                return ValidationResult.fail("blueprint.count_not_enough");
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateRequiredMaterials(Recipe recipe, GuiItems guiItems) {
        if (!onlyContainsAllowedMaterials(recipe, guiItems.requiredMaterials(), false)) {
            return ValidationResult.fail("forge.error.material_not_allowed");
        }
        Map<String, Integer> counts = countMaterials(guiItems.requiredMaterials());
        for (ForgeMaterial material : recipe.requiredMaterials()) {
            if (counts.getOrDefault(material.key(), 0) < Math.max(1, material.amount())) {
                return ValidationResult.fail("material.count_not_enough");
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateOptionalMaterials(Recipe recipe, GuiItems guiItems) {
        if (!onlyContainsAllowedMaterials(recipe, guiItems.optionalMaterials(), true)) {
            return ValidationResult.fail("forge.error.material_not_allowed");
        }
        int occupiedStacks = 0;
        for (ItemStack itemStack : guiItems.optionalMaterials().values()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            occupiedStacks++;
            ForgeMaterial material = recipe.findMaterialBySource(identify(itemStack), true);
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
        int totalCapacity = usagePlanner.optionalCapacityCost(recipe, guiItems);
        int capacityBonus = usagePlanner.optionalCapacityBonus(recipe, guiItems);
        int maxCapacity = Math.max(0, recipe.forgeCapacity()) + capacityBonus;
        if (maxCapacity > 0 && totalCapacity > maxCapacity) {
            return ValidationResult.fail("forge.error.capacity_exceeded", Map.of(
                    "current", totalCapacity,
                    "max", maxCapacity
            ));
        }
        return ValidationResult.ok();
    }

    private boolean onlyContainsAllowedMaterials(Recipe recipe, Map<Integer, ItemStack> inputs, boolean optional) {
        for (ItemStack itemStack : inputs.values()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            ItemSource source = identify(itemStack);
            if (source == null || recipe.findMaterialBySource(source, optional) == null) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Integer> countMaterials(Map<Integer, ItemStack> inputs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack itemStack : inputs.values()) {
            if (isEmpty(itemStack)) {
                continue;
            }
            String key = shorthand(identify(itemStack));
            if (!key.isBlank()) {
                counts.merge(key, itemStack.getAmount(), Integer::sum);
            }
        }
        return counts;
    }

    private ItemSource identify(ItemStack itemStack) {
        return plugin.itemIdentifierService() == null ? null : plugin.itemIdentifierService().identifyItem(itemStack);
    }

    private String shorthand(ItemSource source) {
        if (source == null) {
            return "";
        }
        ForgeMaterial material = null;
        if (plugin.forgeService() != null) {
            material = plugin.forgeService().findMaterialBySource(source);
        }
        if (material != null) {
            return material.key();
        }
        BlueprintRequirement requirement = plugin.forgeService() == null ? null : plugin.forgeService().findBlueprintRequirementBySource(source);
        return requirement == null ? "" : requirement.key();
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }
}
