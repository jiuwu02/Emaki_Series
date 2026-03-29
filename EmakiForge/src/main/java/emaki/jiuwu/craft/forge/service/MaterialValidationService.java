package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.ValidationResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class MaterialValidationService {

    private record MaterialPoolEntry(String materialId, ForgeMaterial material, ItemStack item, int remaining) {
    }

    private record NormalizedGuiItems(List<ItemStack> blueprints,
                                      Map<String, ItemStack> requiredMaterials,
                                      Map<String, ItemStack> optionalMaterials) {
    }

    private final Function<ItemStack, ItemSource> itemIdentifier;
    private final Function<ItemSource, ForgeMaterial> materialResolver;
    private final Function<ItemSource, Blueprint> blueprintResolver;
    private final Function<String, List<Blueprint>> blueprintTagResolver;
    private final Predicate<ItemStack> emptyItemChecker;
    private final ToIntFunction<ItemStack> amountReader;
    private final BiFunction<ItemStack, Integer, ItemStack> amountCloneFactory;

    MaterialValidationService(EmakiForgePlugin plugin, ForgeLookupIndex lookupIndex) {
        this(
            itemStack -> plugin.itemIdentifierService() == null ? null : plugin.itemIdentifierService().identifyItem(itemStack),
            lookupIndex::findMaterialBySource,
            lookupIndex::findBlueprintBySource,
            tag -> plugin.blueprintLoader() == null ? List.of() : plugin.blueprintLoader().getByTag(tag),
            itemStack -> itemStack == null || itemStack.getType() == Material.AIR,
            itemStack -> itemStack == null ? 0 : itemStack.getAmount(),
            (itemStack, amount) -> {
                if (itemStack == null) {
                    return null;
                }
                ItemStack clone = itemStack.clone();
                clone.setAmount(amount);
                return clone;
            }
        );
    }

    MaterialValidationService(Function<ItemStack, ItemSource> itemIdentifier,
                              Function<ItemSource, ForgeMaterial> materialResolver,
                              Function<ItemSource, Blueprint> blueprintResolver,
                              Function<String, List<Blueprint>> blueprintTagResolver,
                              Predicate<ItemStack> emptyItemChecker,
                              ToIntFunction<ItemStack> amountReader,
                              BiFunction<ItemStack, Integer, ItemStack> amountCloneFactory) {
        this.itemIdentifier = itemIdentifier;
        this.materialResolver = materialResolver;
        this.blueprintResolver = blueprintResolver;
        this.blueprintTagResolver = blueprintTagResolver;
        this.emptyItemChecker = emptyItemChecker;
        this.amountReader = amountReader;
        this.amountCloneFactory = amountCloneFactory;
    }

    ValidationResult validate(Recipe recipe, GuiItems guiItems) {
        NormalizedGuiItems normalized = normalizeMaterialInputs(recipe, guiItems);
        if (normalized == null) {
            return ValidationResult.fail("forge.error.material_not_allowed");
        }
        ValidationResult blueprintValidation = validateBlueprints(recipe, normalized.blueprints());
        if (!blueprintValidation.success()) {
            return blueprintValidation;
        }
        ValidationResult requiredValidation = validateRequiredMaterials(recipe, normalized.requiredMaterials());
        if (!requiredValidation.success()) {
            return requiredValidation;
        }
        return validateOptionalMaterials(recipe, normalized.optionalMaterials(), recipe.forgeCapacity());
    }

    boolean acceptsMaterials(Recipe recipe, GuiItems guiItems) {
        return normalizeMaterialInputs(recipe, guiItems) != null;
    }

    private NormalizedGuiItems normalizeMaterialInputs(Recipe recipe, GuiItems guiItems) {
        Map<Integer, ItemStack> requiredMap = guiItems.requiredMaterials() == null ? Map.of() : guiItems.requiredMaterials();
        Map<Integer, ItemStack> optionalMap = guiItems.optionalMaterials() == null ? Map.of() : guiItems.optionalMaterials();
        List<MaterialPoolEntry> pool = new ArrayList<>();
        for (ItemStack itemStack : collectMaterialPool(requiredMap, optionalMap)) {
            if (isEmptyItem(itemStack)) {
                continue;
            }
            ItemSource source = itemIdentifier.apply(itemStack);
            ForgeMaterial material = materialResolver.apply(source);
            if (material == null) {
                return null;
            }
            pool.add(new MaterialPoolEntry(material.id(), material, itemStack, amountOf(itemStack)));
        }
        Map<String, ItemStack> normalizedRequired = new LinkedHashMap<>();
        int index = 0;
        for (Recipe.RequiredMaterial requiredMaterial : recipe.requiredMaterials()) {
            int needed = requiredMaterial.count();
            for (int poolIndex = 0; poolIndex < pool.size() && needed > 0; poolIndex++) {
                MaterialPoolEntry entry = pool.get(poolIndex);
                if (!requiredMaterial.id().equals(entry.materialId()) || entry.remaining() <= 0) {
                    continue;
                }
                int take = Math.min(needed, entry.remaining());
                ItemStack clone = copyWithAmount(entry.item(), take);
                normalizedRequired.put("req_" + index++, clone);
                pool.set(poolIndex, new MaterialPoolEntry(entry.materialId(), entry.material(), entry.item(), entry.remaining() - take));
                needed -= take;
            }
        }
        Map<String, ItemStack> normalizedOptional = new LinkedHashMap<>();
        index = 0;
        for (MaterialPoolEntry entry : pool) {
            if (entry.remaining() <= 0) {
                continue;
            }
            ItemStack clone = copyWithAmount(entry.item(), entry.remaining());
            normalizedOptional.put("opt_" + index++, clone);
        }
        return new NormalizedGuiItems(guiItems.blueprintList(), normalizedRequired, normalizedOptional);
    }

    private List<ItemStack> collectMaterialPool(Map<Integer, ItemStack> requiredMap, Map<Integer, ItemStack> optionalMap) {
        List<ItemStack> pool = new ArrayList<>();
        pool.addAll(requiredMap.values());
        pool.addAll(optionalMap.values());
        return pool;
    }

    private ValidationResult validateBlueprints(Recipe recipe, List<ItemStack> blueprintItems) {
        if (recipe.blueprintRequirements().isEmpty()) {
            return ValidationResult.ok();
        }
        Map<String, Integer> available = new LinkedHashMap<>();
        for (ItemStack itemStack : blueprintItems) {
            if (itemStack == null) {
                continue;
            }
            Blueprint blueprint = blueprintResolver.apply(itemIdentifier.apply(itemStack));
            if (blueprint != null) {
                available.merge(blueprint.id(), amountOf(itemStack), Integer::sum);
            }
        }
        Map<String, Integer> reserved = new LinkedHashMap<>();
        for (Recipe.BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if ("all_of".equals(Texts.lower(requirement.requirementMode()))) {
                for (Recipe.BlueprintOption option : requirement.blueprintOptions()) {
                    if (countMatchingBlueprints(option.selector(), available) < option.count()) {
                        return ValidationResult.fail("blueprint.count_not_enough");
                    }
                    reserveBlueprints(option.selector(), option.count(), available, reserved);
                }
            } else {
                Recipe.BlueprintOption satisfied = null;
                for (Recipe.BlueprintOption option : requirement.blueprintOptions()) {
                    if (countMatchingBlueprints(option.selector(), available) >= option.count()) {
                        satisfied = option;
                        break;
                    }
                }
                if (satisfied == null) {
                    return ValidationResult.fail("blueprint.count_not_enough");
                }
                reserveBlueprints(satisfied.selector(), satisfied.count(), available, reserved);
            }
        }
        return ValidationResult.ok();
    }

    private int countMatchingBlueprints(Map<String, Object> selector, Map<String, Integer> available) {
        String kind = Texts.lower(selector.get("kind"));
        String value = Texts.toStringSafe(selector.get("value"));
        if ("id".equals(kind)) {
            return available.getOrDefault(value, 0);
        }
        int total = 0;
        if ("tag".equals(kind)) {
            for (Blueprint blueprint : blueprintsByTag(value)) {
                total += available.getOrDefault(blueprint.id(), 0);
            }
        }
        return total;
    }

    private void reserveBlueprints(Map<String, Object> selector,
                                   int count,
                                   Map<String, Integer> available,
                                   Map<String, Integer> reserved) {
        String kind = Texts.lower(selector.get("kind"));
        String value = Texts.toStringSafe(selector.get("value"));
        int remaining = count;
        if ("id".equals(kind)) {
            int amount = Math.min(remaining, available.getOrDefault(value, 0));
            reserved.merge(value, amount, Integer::sum);
            available.put(value, available.getOrDefault(value, 0) - amount);
            return;
        }
        if ("tag".equals(kind)) {
            for (Blueprint blueprint : blueprintsByTag(value)) {
                if (remaining <= 0) {
                    break;
                }
                int availableAmount = available.getOrDefault(blueprint.id(), 0);
                int reserve = Math.min(remaining, availableAmount);
                if (reserve <= 0) {
                    continue;
                }
                reserved.merge(blueprint.id(), reserve, Integer::sum);
                available.put(blueprint.id(), availableAmount - reserve);
                remaining -= reserve;
            }
        }
    }

    private ValidationResult validateRequiredMaterials(Recipe recipe, Map<String, ItemStack> requiredMaterials) {
        Map<String, Integer> materialCounts = new LinkedHashMap<>();
        for (ItemStack itemStack : requiredMaterials.values()) {
            ForgeMaterial material = materialResolver.apply(itemIdentifier.apply(itemStack));
            if (material != null) {
                materialCounts.merge(material.id(), amountOf(itemStack), Integer::sum);
            }
        }
        for (Recipe.RequiredMaterial requiredMaterial : recipe.requiredMaterials()) {
            if (materialCounts.getOrDefault(requiredMaterial.id(), 0) < requiredMaterial.count()) {
                return ValidationResult.fail("material.count_not_enough");
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateOptionalMaterials(Recipe recipe, Map<String, ItemStack> optionalMaterials, int maxCapacity) {
        Recipe.OptionalMaterialsConfig optionalConfig = recipe.optionalMaterials();
        if (!optionalConfig.enabled()) {
            for (ItemStack itemStack : optionalMaterials.values()) {
                if (itemStack != null && amountOf(itemStack) > 0) {
                    return ValidationResult.fail("forge.error.material_not_allowed");
                }
            }
            return ValidationResult.ok();
        }
        int totalCapacity = 0;
        int capacityBonus = 0;
        int totalItems = 0;
        for (ItemStack itemStack : optionalMaterials.values()) {
            ForgeMaterial material = materialResolver.apply(itemIdentifier.apply(itemStack));
            if (material == null) {
                continue;
            }
            if (!optionalConfig.whitelist().isEmpty() && !optionalConfig.whitelist().contains(material.id())) {
                return ValidationResult.fail("forge.error.material_not_allowed");
            }
            if (optionalConfig.blacklist().contains(material.id())) {
                return ValidationResult.fail("forge.error.material_blacklisted");
            }
            int amount = amountOf(itemStack);
            totalCapacity += material.effectiveCapacityCost() * amount;
            capacityBonus += material.forgeCapacityBonus() * amount;
            totalItems += amount;
        }
        if (optionalConfig.maxCount() > 0 && totalItems > optionalConfig.maxCount()) {
            return ValidationResult.fail("forge.error.material_count_exceeded", Map.of("current", totalItems, "max", optionalConfig.maxCount()));
        }
        int effectiveMaxCapacity = maxCapacity + capacityBonus;
        if (effectiveMaxCapacity > 0 && totalCapacity > effectiveMaxCapacity) {
            return ValidationResult.fail("forge.error.capacity_exceeded", Map.of("current", totalCapacity, "max", effectiveMaxCapacity));
        }
        return ValidationResult.ok();
    }

    private List<Blueprint> blueprintsByTag(String tag) {
        List<Blueprint> blueprints = blueprintTagResolver == null ? null : blueprintTagResolver.apply(tag);
        return blueprints == null ? List.of() : blueprints;
    }

    private boolean isEmptyItem(ItemStack itemStack) {
        return emptyItemChecker == null ? itemStack == null : emptyItemChecker.test(itemStack);
    }

    private int amountOf(ItemStack itemStack) {
        return amountReader == null || itemStack == null ? 0 : amountReader.applyAsInt(itemStack);
    }

    private ItemStack copyWithAmount(ItemStack itemStack, int amount) {
        if (itemStack == null || amountCloneFactory == null) {
            return null;
        }
        return amountCloneFactory.apply(itemStack, amount);
    }
}
