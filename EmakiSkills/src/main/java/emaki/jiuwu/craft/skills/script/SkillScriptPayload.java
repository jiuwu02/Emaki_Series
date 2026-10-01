package emaki.jiuwu.craft.skills.script;

import java.util.List;
import java.util.Map;

record SkillScriptPayload(
        String id,
        String displayName,
        List<String> description,
        String iconMaterial,
        String activationType,
        List<String> passiveTriggers,
        long cooldownTicks,
        long globalCooldownTicks,
        List<String> tags,
        List<String> loreAliases,
        String pdcSkillId,
        String uiCategory,
        int sortOrder,
        Boolean showInSlots,
        Boolean enabled,
        Map<String, List<String>> scriptLines,
        Map<String, List<String>> scriptConditions,
        String mythicSkill) {

    static final SkillScriptPayload EMPTY = new SkillScriptPayload(
            "",
            "",
            List.of(),
            "",
            "",
            List.of(),
            0L,
            0L,
            List.of(),
            List.of(),
            "",
            "",
            0,
            null,
            null,
            Map.of(),
            Map.of(),
            "");
}
