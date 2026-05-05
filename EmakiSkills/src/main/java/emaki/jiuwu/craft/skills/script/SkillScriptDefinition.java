package emaki.jiuwu.craft.skills.script;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record SkillScriptDefinition(
        boolean enabled,
        SkillScriptMode mode,
        boolean stopOnFailure,
        Map<SkillScriptPhase, List<String>> conditionsByPhase,
        Map<SkillScriptPhase, List<String>> linesByPhase
) {

    public SkillScriptDefinition {
        mode = mode == null ? SkillScriptMode.NATIVE : mode;
        conditionsByPhase = copyPhaseMap(conditionsByPhase);
        linesByPhase = copyPhaseMap(linesByPhase);
    }

    public static SkillScriptDefinition disabled() {
        return new SkillScriptDefinition(false, SkillScriptMode.NATIVE, true, Map.of(), Map.of());
    }

    public List<String> conditions(SkillScriptPhase phase) {
        return conditionsByPhase.getOrDefault(phase, List.of());
    }

    public List<String> lines(SkillScriptPhase phase) {
        return linesByPhase.getOrDefault(phase, List.of());
    }

    public boolean hasLines() {
        for (List<String> lines : linesByPhase.values()) {
            if (lines != null && !lines.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Map<SkillScriptPhase, List<String>> copyPhaseMap(Map<SkillScriptPhase, List<String>> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        EnumMap<SkillScriptPhase, List<String>> copy = new EnumMap<>(SkillScriptPhase.class);
        for (Map.Entry<SkillScriptPhase, List<String>> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            List<String> values = entry.getValue() == null ? List.of() : List.copyOf(entry.getValue());
            if (!values.isEmpty()) {
                copy.put(entry.getKey(), values);
            }
        }
        return Map.copyOf(copy);
    }
}
