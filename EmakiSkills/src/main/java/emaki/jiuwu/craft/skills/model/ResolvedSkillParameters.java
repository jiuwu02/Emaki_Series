package emaki.jiuwu.craft.skills.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ResolvedSkillParameters(Map<String, String> values) {

    public ResolvedSkillParameters {
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    public static ResolvedSkillParameters empty() {
        return new ResolvedSkillParameters(Map.of());
    }
}
