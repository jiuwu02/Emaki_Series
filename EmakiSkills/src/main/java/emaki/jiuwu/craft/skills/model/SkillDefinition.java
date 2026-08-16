package emaki.jiuwu.craft.skills.model;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.skills.script.SkillScriptDefinition;

public record SkillDefinition(String id,
        String displayName,
        List<String> description,
        String iconMaterial,
        String mythicSkill,
        SkillActivationType activationType,
        List<String> passiveTriggers,
        String cronExpression,
        int cronMaxExecutions,
        Map<String, SkillParameterDefinition> variables,
        SkillScriptDefinition script,
        SkillUpgradeConfig upgrade,
        long cooldownTicks,
        long globalCooldownTicks,
        List<SkillResourceCost> resourceCosts,
        List<String> loreAliases,
        String pdcSkillId,
        List<String> tags,
        List<String> tabTags,
        List<String> requiredSkillIds,
        List<String> conflictingSkillIds,
        String uiCategory,
        int sortOrder,
        boolean showInSlots,
        boolean enabled,
        ConditionGroup conditions,
        String conditionType) {

    public SkillDefinition {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        description = description == null ? List.of() : List.copyOf(description);
        iconMaterial = iconMaterial == null ? "" : iconMaterial;
        mythicSkill = mythicSkill == null ? "" : mythicSkill;
        activationType = activationType == null ? SkillActivationType.ACTIVE : activationType;
        passiveTriggers = passiveTriggers == null ? List.of() : List.copyOf(passiveTriggers);
        cronExpression = cronExpression == null ? "" : cronExpression;
        cronMaxExecutions = Math.max(0, cronMaxExecutions);
        variables = variables == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        script = script == null ? SkillScriptDefinition.disabled() : script;
        upgrade = upgrade == null ? SkillUpgradeConfig.disabled() : upgrade;
        cooldownTicks = Math.max(0L, cooldownTicks);
        globalCooldownTicks = Math.max(0L, globalCooldownTicks);
        resourceCosts = resourceCosts == null ? List.of() : List.copyOf(resourceCosts);
        loreAliases = loreAliases == null ? List.of() : List.copyOf(loreAliases);
        tags = tags == null ? List.of() : List.copyOf(tags);
        tabTags = tabTags == null ? List.of() : List.copyOf(tabTags);
        requiredSkillIds = requiredSkillIds == null ? List.of() : List.copyOf(requiredSkillIds);
        conflictingSkillIds = conflictingSkillIds == null ? List.of() : List.copyOf(conflictingSkillIds);
        uiCategory = uiCategory == null || uiCategory.isBlank() ? "default" : uiCategory;
        conditions = conditions == null ? ConditionGroup.empty() : conditions;
        conditionType = conditionType == null || conditionType.isBlank() ? "all_of" : conditionType;
    }
}
