package emaki.jiuwu.craft.skills.model;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record SkillDefinition(String id,
        String displayName,
        List<String> description,
        String iconMaterial,
        String mythicSkill,
        SkillActivationType activationType,
        List<String> passiveTriggers,
        Map<String, SkillParameterDefinition> skillParameters,
        SkillUpgradeConfig upgrade,
        long cooldownTicks,
        long globalCooldownTicks,
        List<SkillResourceCost> resourceCosts,
        List<String> loreAliases,
        String pdcSkillId,
        String uiCategory,
        int sortOrder,
        boolean enabled) {

    public SkillDefinition {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        description = description == null ? List.of() : List.copyOf(description);
        iconMaterial = iconMaterial == null ? "" : iconMaterial;
        mythicSkill = mythicSkill == null ? "" : mythicSkill;
        activationType = activationType == null ? SkillActivationType.ACTIVE : activationType;
        passiveTriggers = passiveTriggers == null ? List.of() : List.copyOf(passiveTriggers);
        skillParameters = skillParameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(skillParameters));
        upgrade = upgrade == null ? SkillUpgradeConfig.disabled() : upgrade;
        cooldownTicks = Math.max(0L, cooldownTicks);
        globalCooldownTicks = Math.max(0L, globalCooldownTicks);
        resourceCosts = resourceCosts == null ? List.of() : List.copyOf(resourceCosts);
        loreAliases = loreAliases == null ? List.of() : List.copyOf(loreAliases);
        uiCategory = uiCategory == null || uiCategory.isBlank() ? "default" : uiCategory;
    }
}
