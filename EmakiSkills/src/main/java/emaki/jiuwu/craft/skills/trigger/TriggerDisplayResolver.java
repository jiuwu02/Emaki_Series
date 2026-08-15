package emaki.jiuwu.craft.skills.trigger;

import java.util.Map;

public final class TriggerDisplayResolver {

    private TriggerDisplayResolver() {
    }

    public static String resolve(String triggerId, Map<String, SkillTriggerDefinition> definitions) {
        SkillTriggerDefinition def = definitions.get(triggerId);
        if (def != null) {
            return def.displayName();
        }

        return "[" + triggerId + "]";
    }
}
