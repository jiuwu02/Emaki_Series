package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;

final class MaterialPlanResolver {

    private final StrengthenRecipeResolver recipeResolver;

    MaterialPlanResolver(StrengthenRecipeResolver recipeResolver) {
        this.recipeResolver = recipeResolver;
    }

    MaterialPlan resolveMaterialPlan(AttemptContext context, StrengthenRecipe.StarStage stage) {
        if (stage == null) {
            return new MaterialPlan("strengthen.error.material_missing", List.of(), List.of(), false, 0);
        }
        Map<String, StrengthenRecipe.StarStageMaterial> materialsByItem = new LinkedHashMap<>();
        for (StrengthenRecipe.StarStageMaterial material : stage.materials()) {
            if (material != null && Texts.isNotBlank(material.item())) {
                materialsByItem.putIfAbsent(Texts.lower(material.item()), material);
            }
        }

        List<ItemStack> inputs = context == null ? List.of() : context.materialInputs();
        List<String> matchedTokens = new ArrayList<>(inputs.size());
        Map<String, Integer> availableByItem = new LinkedHashMap<>();
        for (ItemStack input : inputs) {
            if (input == null || input.getType().isAir()) {
                matchedTokens.add("");
                continue;
            }
            String token = Texts.lower(resolveItemToken(input));
            StrengthenRecipe.StarStageMaterial matched = materialsByItem.get(token);
            if (matched == null) {
                return new MaterialPlan(
                        "strengthen.error.invalid_optional_material",
                        buildRequiredMaterials(stage, availableByItem),
                        buildEmptyOptionalMaterials(context),
                        false,
                        0
                );
            }
            matchedTokens.add(token);
            availableByItem.merge(token, input.getAmount(), Integer::sum);
        }

        List<AttemptMaterial> requiredMaterials = buildRequiredMaterials(stage, availableByItem);
        for (AttemptMaterial material : requiredMaterials) {
            if (!material.satisfied()) {
                return new MaterialPlan(
                        "strengthen.error.material_missing",
                        requiredMaterials,
                        buildGuiMaterials(inputs, matchedTokens, materialsByItem, new LinkedHashMap<>()),
                        false,
                        0
                );
            }
        }

        Map<String, Integer> remainingConsumes = new LinkedHashMap<>();
        boolean protectionApplied = false;
        for (Map.Entry<String, StrengthenRecipe.StarStageMaterial> entry : materialsByItem.entrySet()) {
            int available = availableByItem.getOrDefault(entry.getKey(), 0);
            remainingConsumes.put(entry.getKey(), resolveTotalConsumeAmount(entry.getValue(), available));
            protectionApplied = protectionApplied || (entry.getValue().protection() && available > 0);
        }

        List<AttemptMaterial> guiMaterials = buildGuiMaterials(inputs, matchedTokens, materialsByItem, remainingConsumes);
        int temperBonus = 0;
        for (AttemptMaterial material : guiMaterials) {
            if (material == null || material.consumedAmount() <= 0) {
                continue;
            }
            temperBonus += material.consumedAmount() * material.temperBoost();
        }

        return new MaterialPlan("", requiredMaterials, guiMaterials, protectionApplied, temperBonus);
    }

    private List<AttemptMaterial> buildRequiredMaterials(StrengthenRecipe.StarStage stage, Map<String, Integer> availableByItem) {
        if (stage == null || stage.materials().isEmpty()) {
            return List.of();
        }
        List<AttemptMaterial> requiredMaterials = new ArrayList<>();
        for (StrengthenRecipe.StarStageMaterial material : stage.materials()) {
            if (material == null || Texts.isBlank(material.item())) {
                continue;
            }
            int available = availableByItem.getOrDefault(Texts.lower(material.item()), 0);
            int requiredAmount = resolveRequiredGuiAmount(material);
            requiredMaterials.add(new AttemptMaterial(
                    material.item(),
                    requiredAmount,
                    available,
                    false,
                    material.protection(),
                    material.temperBoost(),
                    resolveTotalConsumeAmount(material, available)
            ));
        }
        return List.copyOf(requiredMaterials);
    }

    private List<AttemptMaterial> buildGuiMaterials(List<ItemStack> inputs,
            List<String> matchedTokens,
            Map<String, StrengthenRecipe.StarStageMaterial> materialsByItem,
            Map<String, Integer> remainingConsumes) {
        List<AttemptMaterial> optionalMaterials = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            ItemStack input = inputs.get(index);
            if (input == null || input.getType().isAir()) {
                optionalMaterials.add(new AttemptMaterial("", 0, 0, true, false, 0, 0));
                continue;
            }
            String token = index < matchedTokens.size() ? matchedTokens.get(index) : "";
            StrengthenRecipe.StarStageMaterial matched = materialsByItem.get(token);
            if (matched == null) {
                optionalMaterials.add(new AttemptMaterial("", 0, 0, true, false, 0, 0));
                continue;
            }
            int available = input.getAmount();
            int consumed = Math.min(available, Math.max(0, remainingConsumes.getOrDefault(token, 0)));
            if (consumed > 0) {
                remainingConsumes.put(token, Math.max(0, remainingConsumes.get(token) - consumed));
            }
            optionalMaterials.add(new AttemptMaterial(
                    matched.item(),
                    matched.amount(),
                    available,
                    matched.optional(),
                    matched.protection(),
                    matched.temperBoost(),
                    consumed
            ));
        }
        return List.copyOf(optionalMaterials);
    }

    private List<AttemptMaterial> buildEmptyOptionalMaterials(AttemptContext context) {
        List<ItemStack> inputs = context == null ? List.of() : context.materialInputs();
        List<AttemptMaterial> result = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            result.add(new AttemptMaterial("", 0, 0, true, false, 0, 0));
        }
        return List.copyOf(result);
    }

    private int resolveTotalConsumeAmount(StrengthenRecipe.StarStageMaterial material, int available) {
        if (material == null || available <= 0) {
            return 0;
        }
        if (material.protection()) {
            return 1;
        }
        if (material.amount() > 0) {
            return Math.min(material.amount(), available);
        }
        return available;
    }

    private int resolveRequiredGuiAmount(StrengthenRecipe.StarStageMaterial material) {
        if (material == null) {
            return 1;
        }
        return material.amount() < 0 ? 1 : Math.max(1, material.amount());
    }

    private String resolveItemToken(ItemStack itemStack) {
        ItemSource source = recipeResolver.resolveBaseSource(itemStack);
        return source == null ? "" : ItemSourceUtil.toShorthand(source);
    }

    record MaterialPlan(String errorKey,
            List<AttemptMaterial> requiredMaterials,
            List<AttemptMaterial> optionalMaterials,
            boolean protectionApplied,
            int appliedTemperBonus) {

    }
}
