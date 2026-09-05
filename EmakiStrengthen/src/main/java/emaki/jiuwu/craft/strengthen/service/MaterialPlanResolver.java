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
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
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
        return resolveMaterialPlan(context, stage, null, "", "");
    }

    MaterialPlan resolveMaterialPlan(AttemptContext context,
            StrengthenRecipe.StarStage stage,
            @Nullable Player player,
            String recipeId) {
        return resolveMaterialPlan(context, stage, player, recipeId, "");
    }

    MaterialPlan resolveMaterialPlan(AttemptContext context,
            StrengthenRecipe.StarStage stage,
            @Nullable Player player,
            String recipeId,
            String branchPath) {
        if (stage == null) {
            return new MaterialPlan("strengthen.error.material_missing", List.of(), List.of(), false, 0);
        }
        List<ItemStack> inputs = context == null ? List.of() : context.materialInputs();
        ItemStack target = context == null ? null : context.targetItem();
        ItemSourceRef targetSource = target == null ? null : recipeResolver.resolveBaseSource(target);
        List<InputState> inputStates = createInputStates(inputs);
        List<MaterialIdentityPlanner.Definition> definitions = new ArrayList<>();
        Map<Integer, StrengthenRecipe.StarStageMaterial> materialByOrder = new LinkedHashMap<>();
        Map<Integer, InputState> inputByIndex = new LinkedHashMap<>();
        for (InputState input : inputStates) {
            inputByIndex.put(input.index(), input);
        }
        for (int definitionIndex = 0; definitionIndex < stage.materials().size(); definitionIndex++) {
            StrengthenRecipe.StarStageMaterial material = stage.materials().get(definitionIndex);
            if (material == null || Texts.isBlank(material.materialId())) {
                return new MaterialPlan("strengthen.error.material_missing", List.of(), List.of(), false, 0);
            }
            materialByOrder.put(definitionIndex, material);
            StarStageMaterialRule rule = materialRule(recipeId, stage.targetStar(), material, branchPath);
            definitions.add(new MaterialIdentityPlanner.Definition(definitionIndex, material.materialId(),
                    material.countKey(), material.amount(), material.optional(), material.protection(),
                    material.temperBoost(), material.matcherConfig() != null || rule.constrains()));
        }
        List<MaterialIdentityPlanner.Input> plannerInputs = inputStates.stream()
                .filter(input -> input.stack() != null && !input.stack().getType().isAir())
                .map(input -> new MaterialIdentityPlanner.Input(input.index(), input.stack().getAmount()))
                .toList();
        Map<String, Boolean> matchCache = new LinkedHashMap<>();
        MaterialIdentityPlanner.Plan planned = MaterialIdentityPlanner.plan(definitions, plannerInputs, (definition, input) -> {
            String key = definition.order() + "|" + input.index();
            return matchCache.computeIfAbsent(key, ignored -> {
                StrengthenRecipe.StarStageMaterial material = materialByOrder.get(definition.order());
                InputState state = inputByIndex.get(input.index());
                return material != null && state != null && matches(material, recipeId, stage.targetStar(), state.stack(),
                        state.source(), target, targetSource, player, branchPath);
            });
        });
        List<DefinitionAllocation> allocations = toAllocations(planned, materialByOrder, inputByIndex);
        if (!planned.satisfied()) {
            return new MaterialPlan("strengthen.error.material_missing",
                    requiredMaterials(stage, allocations, -1, null, 0),
                    inputMaterials(inputStates, definitions, planned), false, 0);
        }
        for (InputState input : inputStates) {
            if (input.stack() == null || input.stack().getType().isAir()) {
                continue;
            }
            boolean recognized = false;
            for (int definitionIndex = 0; definitionIndex < stage.materials().size(); definitionIndex++) {
                String key = definitionIndex + "|" + input.index();
                Boolean matched = matchCache.get(key);
                if (matched == null) {
                    StrengthenRecipe.StarStageMaterial material = materialByOrder.get(definitionIndex);
                    matched = material != null && matches(material, recipeId, stage.targetStar(), input.stack(),
                            input.source(), target, targetSource, player, branchPath);
                    matchCache.put(key, matched);
                }
                if (matched) {
                    recognized = true;
                    break;
                }
            }
            if (!recognized) {
                return new MaterialPlan("strengthen.error.invalid_optional_material",
                        requiredMaterials(stage, allocations, -1, null, 0),
                        inputMaterials(inputStates, definitions, planned), false, 0);
            }
        }

        List<AttemptMaterial> required = requiredMaterials(stage, allocations, -1, null, 0);
        List<AttemptMaterial> inputMaterials = inputMaterials(inputStates, definitions, planned);
        boolean protection = false;
        int temperBonus = 0;
        for (DefinitionAllocation allocation : allocations) {
            int consumed = allocation.consumed();
            if (allocation.material().protection() && allocation.assigned() > 0) {
                protection = true;
            }
            temperBonus += consumed * allocation.material().temperBoost();
        }
        return new MaterialPlan("", required, inputMaterials, protection, temperBonus);
    }

    private List<InputState> createInputStates(List<ItemStack> inputs) {
        List<InputState> states = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            ItemStack stack = inputs.get(index);
            ItemSourceRef source = stack == null || stack.getType().isAir() ? null : recipeResolver.resolveBaseSource(stack);
            states.add(new InputState(index, stack, source, Texts.toStringSafe(ItemSourceUtil.toShorthand(source))));
        }
        return List.copyOf(states);
    }

    private List<DefinitionAllocation> toAllocations(MaterialIdentityPlanner.Plan plan,
            Map<Integer, StrengthenRecipe.StarStageMaterial> materialByOrder,
            Map<Integer, InputState> inputByIndex) {
        Map<Integer, List<InputAllocation>> entriesByOrder = new LinkedHashMap<>();
        Map<Integer, Integer> assignedByOrder = new LinkedHashMap<>();
        for (MaterialIdentityPlanner.Allocation allocation : plan.allocations()) {
            InputState input = inputByIndex.get(allocation.inputIndex());
            if (input == null) {
                continue;
            }
            entriesByOrder.computeIfAbsent(allocation.definitionOrder(), ignored -> new ArrayList<>())
                    .add(new InputAllocation(input, allocation.assigned(), allocation.consumed()));
            assignedByOrder.merge(allocation.definitionOrder(), allocation.assigned(), Integer::sum);
        }
        List<DefinitionAllocation> result = new ArrayList<>();
        for (Map.Entry<Integer, StrengthenRecipe.StarStageMaterial> entry : materialByOrder.entrySet()) {
            result.add(new DefinitionAllocation(entry.getKey(), entry.getValue(),
                    assignedByOrder.getOrDefault(entry.getKey(), 0),
                    List.copyOf(entriesByOrder.getOrDefault(entry.getKey(), List.of()))));
        }
        return List.copyOf(result);
    }

    private boolean matches(StrengthenRecipe.StarStageMaterial material,
            String recipeId,
            int targetStar,
            ItemStack candidate,
            @Nullable ItemSourceRef candidateSource,
            @Nullable ItemStack target,
            @Nullable ItemSourceRef targetSource,
            @Nullable Player player,
            String branchPath) {
        try {
            VariableContext variables = EnhancementMaterialVariables.enrich(
                    VariableContext.builder(player).build(), candidate, target, targetStar, null, null);
            StarStageMaterialRule rule = materialRule(recipeId, targetStar, material, branchPath);
            if (!satisfiesCompare(rule.targetCompare(), variables)) {
                return false;
            }
            MatchContext context = new MatchContext(candidate, candidateSource, player, target, targetSource, variables);
            List<ItemSourceRef> sources = new ArrayList<>();
            for (String source : material.itemSources()) {
                ItemSourceRef parsed = ItemSourceUtil.parse(source);
                if (parsed != null) {
                    sources.add(parsed);
                }
            }
            Matcher matcher = material.matcherConfig() == null ? rule.matcher() : Matcher.fromConfig(material.matcherConfig());
            ItemRequirement requirement = new ItemRequirement(sources, matcher, material.materialId());
            return requirement.test(context);
        } catch (RuntimeException | LinkageError exception) {
            warnRuleFailure(exception);
            return false;
        }
    }

    private StarStageMaterialRule materialRule(String recipeId,
            int targetStar,
            StrengthenRecipe.StarStageMaterial material,
            String branchPath) {
        if (plugin == null || Texts.isBlank(recipeId) || plugin.recipeLoader() == null) {
            return StarStageMaterialRule.inert();
        }
        return plugin.recipeLoader().materialRule(recipeId, targetStar, material.materialId(), branchPath);
    }

    private List<AttemptMaterial> requiredMaterials(StrengthenRecipe.StarStage stage,
            List<DefinitionAllocation> allocations,
            int pendingIndex,
            @Nullable StrengthenRecipe.StarStageMaterial pending,
            int pendingRequired) {
        Map<Integer, DefinitionAllocation> byIndex = new LinkedHashMap<>();
        for (DefinitionAllocation allocation : allocations) {
            byIndex.put(allocation.definitionIndex(), allocation);
        }
        List<AttemptMaterial> result = new ArrayList<>();
        for (int index = 0; index < stage.materials().size(); index++) {
            StrengthenRecipe.StarStageMaterial material = index == pendingIndex ? pending : stage.materials().get(index);
            if (material == null || Texts.isBlank(material.materialId()) || material.optional()) {
                continue;
            }
            DefinitionAllocation allocation = byIndex.get(index);
            int required = index == pendingIndex ? pendingRequired : requiredAmount(material);
            int available = allocation == null ? 0 : allocation.assigned();
            result.add(new AttemptMaterial(material.item(), required, available, material.optional(), material.protection(),
                    material.temperBoost(), allocation == null ? 0 : allocation.consumed(), material.materialId(),
                    material.countKey(), -1, String.join(",", material.itemSources())));
        }
        return List.copyOf(result);
    }

    private List<AttemptMaterial> inputMaterials(List<InputState> inputs,
            List<MaterialIdentityPlanner.Definition> definitions,
            MaterialIdentityPlanner.Plan plan) {
        List<MaterialAttemptProjection.Input> projectionInputs = inputs.stream()
                .map(input -> new MaterialAttemptProjection.Input(input.index(),
                        input.stack() == null ? 0 : input.stack().getAmount(),
                        input.auditSourceToken(), input.auditSourceToken()))
                .toList();
        return MaterialAttemptProjection.project(definitions, projectionInputs, plan);
    }

    private static int requiredAmount(StrengthenRecipe.StarStageMaterial material) {
        return material == null ? 1 : material.amount() < 0 ? 1 : Math.max(1, material.amount());
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
            plugin.getLogger().warning("星级材料规则判定失败，视为不匹配: "
                    + String.valueOf(throwable.getMessage()));
        }
    }

    record MaterialPlan(String errorKey,
            List<AttemptMaterial> requiredMaterials,
            List<AttemptMaterial> optionalMaterials,
            boolean protectionApplied,
            int appliedTemperBonus) {
    }

    private record InputState(int index,
            @Nullable ItemStack stack,
            @Nullable ItemSourceRef source,
            String auditSourceToken) {
    }

    private record InputAllocation(InputState input, int assigned, int consumed) {
    }

    private record DefinitionAllocation(int definitionIndex,
            StrengthenRecipe.StarStageMaterial material,
            int assigned,
            List<InputAllocation> entries) {
        private int consumed() {
            int total = 0;
            for (InputAllocation entry : entries) {
                total += entry.consumed();
            }
            return total;
        }
    }
}
