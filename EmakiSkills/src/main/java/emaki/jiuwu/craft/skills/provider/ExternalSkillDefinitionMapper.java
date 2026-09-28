package emaki.jiuwu.craft.skills.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.skills.api.ExternalSkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillUpgradeConfig;
import emaki.jiuwu.craft.skills.script.SkillScriptDefinition;
import emaki.jiuwu.craft.skills.script.SkillScriptMode;
import emaki.jiuwu.craft.skills.script.SkillScriptPhase;

final class ExternalSkillDefinitionMapper {

    private ExternalSkillDefinitionMapper() {
    }

    static SkillDefinition toDefinition(ExternalSkillDefinition definition, String id) {
        Map<SkillScriptPhase, List<String>> lines = phaseLines(definition.scriptLines());
        Map<SkillScriptPhase, List<String>> conditions = phaseLines(definition.scriptConditions());
        SkillScriptDefinition script = lines.isEmpty()
                ? SkillScriptDefinition.disabled()
                : new SkillScriptDefinition(true, SkillScriptMode.NATIVE, true, conditions, lines);
        ConditionGroup conditionGroup = ConditionGroup.empty();
        String pdcSkillId = Texts.isBlank(definition.pdcSkillId())
                ? id
                : Texts.normalizeId(definition.pdcSkillId());
        return new SkillDefinition(
                id,
                definition.displayName(),
                definition.description(),
                Texts.lower(definition.iconMaterial()),
                Texts.toStringSafe(definition.mythicSkill()).trim(),
                SkillActivationType.fromString(definition.activationType()),
                normalizeTriggerIds(definition.passiveTriggers()),
                "",
                0,
                Map.of(),
                script,
                SkillUpgradeConfig.disabled(),
                Math.max(0L, definition.cooldownTicks()),
                Math.max(0L, definition.globalCooldownTicks()),
                List.of(),
                definition.loreAliases(),
                pdcSkillId,
                normalizeIds(definition.tags()),
                List.of(),
                List.of(),
                List.of(),
                Texts.isBlank(definition.uiCategory()) ? "default" : definition.uiCategory(),
                definition.sortOrder(),
                definition.showInSlots(),
                definition.enabled(),
                conditionGroup,
                conditionGroup.conditionType()
        );
    }

    private static Map<SkillScriptPhase, List<String>> phaseLines(Map<String, List<String>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<SkillScriptPhase, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
            SkillScriptPhase phase = SkillScriptPhase.fromString(entry.getKey());
            List<String> source = entry.getValue();
            if (phase == null || source == null || source.isEmpty()) {
                continue;
            }
            List<String> lines = new ArrayList<>(source.size());
            for (String line : source) {
                if (Texts.isNotBlank(line)) {
                    lines.add(line.trim());
                }
            }
            if (!lines.isEmpty()) {
                result.put(phase, List.copyOf(lines));
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static List<String> normalizeIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : raw) {
            String id = Texts.normalizeId(value).replace('-', '_');
            if (Texts.isNotBlank(id)) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }

    private static List<String> normalizeTriggerIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String value : raw) {
            String id = Texts.lower(value).replace('-', '_').trim();
            if (!id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }
}
