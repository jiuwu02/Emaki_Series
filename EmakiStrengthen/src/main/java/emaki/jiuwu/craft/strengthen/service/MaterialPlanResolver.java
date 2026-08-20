package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementMaterialVariables;
import emaki.jiuwu.craft.strengthen.enhancement.cost.TargetCompareEnum;
import emaki.jiuwu.craft.strengthen.model.StarStageMaterialRule;

final class MaterialPlanResolver {

    private final StrengthenRecipeResolver recipeResolver;
    private final EmakiStrengthenPlugin plugin;

    MaterialPlanResolver(StrengthenRecipeResolver recipeResolver) {
        this(recipeResolver, null);
    }

    MaterialPlanResolver(StrengthenRecipeResolver recipeResolver, EmakiStrengthenPlugin plugin) {
        this.recipeResolver = recipeResolver;
        this.plugin = plugin;
    }

    MaterialPlan resolveMaterialPlan(AttemptContext context, StrengthenRecipe.StarStage stage) {
        return resolveMaterialPlan(context, stage, null, "");
    }

    MaterialPlan resolveMaterialPlan(AttemptContext context,
            StrengthenRecipe.StarStage stage,
            @Nullable Player player,
            String recipeId) {
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
        ItemStack targetItem = context == null ? null : context.targetItem();
        ItemSourceRef targetSource = targetItem == null ? null : recipeResolver.resolveBaseSource(targetItem);
        List<String> matchedTokens = new ArrayList<>(inputs.size());
        Map<String, Integer> availableByItem = new LinkedHashMap<>();
        for (ItemStack input : inputs) {
            if (input == null || input.getType().isAir()) {
                matchedTokens.add("");
                continue;
            }
            String token = Texts.lower(resolveItemToken(input));
            StrengthenRecipe.StarStageMaterial matched = materialsByItem.get(token);
            if (matched != null && !satisfiesTargetAwareRule(recipeId, stage.targetStar(), token, input,
                    targetItem, targetSource, player)) {
                matched = null;
            }
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
        ItemSourceRef source = recipeResolver.resolveBaseSource(itemStack);
        return source == null ? "" : ItemSourceUtil.toShorthand(source);
    }

    private boolean satisfiesTargetAwareRule(String recipeId,
            int targetStar,
            String itemToken,
            ItemStack candidate,
            @Nullable ItemStack targetItem,
            @Nullable ItemSourceRef targetSource,
            @Nullable Player player) {
        if (plugin == null || Texts.isBlank(recipeId) || plugin.recipeLoader() == null) {
            return true;
        }
        StarStageMaterialRule rule = plugin.recipeLoader().materialRule(recipeId, targetStar, itemToken);
        if (!rule.constrains()) {
            return true;
        }
        try {
            VariableContext variables = EnhancementMaterialVariables.enrich(
                    VariableContext.builder(player).build(), candidate, targetItem, targetStar, null, null);
            if (!satisfiesCompare(rule.targetCompare(), variables)) {
                return false;
            }
            Matcher matcher = rule.matcher();
            if (matcher == null) {
                return true;
            }
            MatchContext matchContext = new MatchContext(candidate,
                    recipeResolver.resolveBaseSource(candidate), player, targetItem, targetSource, variables);
            return matcher.test(matchContext);
        } catch (RuntimeException | LinkageError exception) {
            warnRuleFailure(exception);
            return false;
        }
    }

    private static boolean satisfiesCompare(TargetCompareEnum compare, VariableContext variables) {
        Map<String, Object> values = variables.toMap();
        return switch (compare) {
            case NONE -> true;
            case SAME_AFFIX -> flag(values, EnhancementMaterialVariables.VARIABLE_SHARED_AFFIX_COUNT) > 0;
            case SAME_AFFIX_SET -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_AFFIX_SET) == 1;
            case SAME_ITEM_TYPE -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_ITEM_TYPE) == 1;
            case SAME_LEVEL -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_LEVEL) == 1;
            case LEVEL_AT_LEAST -> flag(values, EnhancementMaterialVariables.VARIABLE_LEVEL_AT_LEAST_TARGET) == 1;
        };
    }

    private static int flag(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void warnRuleFailure(Throwable throwable) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().warning("星级材料目标感知规则判定失败，视为不匹配: "
                    + String.valueOf(throwable.getMessage()));
        }
    }

    record MaterialPlan(String errorKey,
            List<AttemptMaterial> requiredMaterials,
            List<AttemptMaterial> optionalMaterials,
            boolean protectionApplied,
            int appliedTemperBonus) {

    }
}
