package emaki.jiuwu.craft.skills.model;

import java.util.List;

public record SkillDefinition(String id,
        String displayName,
        List<String> description,
        String iconSource,
        String mythicSkill,
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
        iconSource = iconSource == null ? "" : iconSource;
        mythicSkill = mythicSkill == null ? "" : mythicSkill;
        cooldownTicks = Math.max(0L, cooldownTicks);
        globalCooldownTicks = Math.max(0L, globalCooldownTicks);
        resourceCosts = resourceCosts == null ? List.of() : List.copyOf(resourceCosts);
        loreAliases = loreAliases == null ? List.of() : List.copyOf(loreAliases);
        uiCategory = uiCategory == null || uiCategory.isBlank() ? "default" : uiCategory;
    }

    public String iconMaterial() {
        return iconSource;
    }
}
