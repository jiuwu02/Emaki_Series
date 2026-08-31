package emaki.jiuwu.craft.corelib.trigger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class TriggerConflictResolver {

    private static final Logger LOGGER = Logger.getLogger(TriggerConflictResolver.class.getName());

    private final Map<String, Set<String>> conflictMatrix = new HashMap<>();

    public void buildFromDefinitions(Map<String, TriggerDefinition> definitions) {
        conflictMatrix.clear();

        for (String id : definitions.keySet()) {
            conflictMatrix.computeIfAbsent(id, k -> new HashSet<>()).add(id);
        }

        for (TriggerDefinition def : definitions.values()) {
            for (String other : def.incompatibleWith()) {
                if (!definitions.containsKey(other)) {
                    LOGGER.warning("Trigger '" + def.id()
                            + "' declares incompatibility with unknown trigger '" + other + "'");
                    continue;
                }
                conflictMatrix.computeIfAbsent(def.id(), k -> new HashSet<>()).add(other);
                conflictMatrix.computeIfAbsent(other, k -> new HashSet<>()).add(def.id());
            }
        }
    }

    public boolean conflicts(String triggerId1, String triggerId2) {
        Set<String> set = conflictMatrix.get(triggerId1);
        return set != null && set.contains(triggerId2);
    }

    public Set<String> getConflicts(String triggerId) {
        Set<String> set = conflictMatrix.get(triggerId);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public void clear() {
        conflictMatrix.clear();
    }
}
