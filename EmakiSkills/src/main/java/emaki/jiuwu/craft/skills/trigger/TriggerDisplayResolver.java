package emaki.jiuwu.craft.skills.trigger;

import java.util.Map;

import emaki.jiuwu.craft.corelib.trigger.TriggerDefinition;

public final class TriggerDisplayResolver {

    private TriggerDisplayResolver() {
    }

    public static String resolve(String triggerId, Map<String, TriggerDefinition> definitions) {
        TriggerDefinition def = definitions.get(triggerId);
        if (def != null) {
            return def.displayName();
        }

        return "[" + triggerId + "]";
    }
}
