package emaki.jiuwu.craft.corelib.trigger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.log.EmakiLog;

public final class TriggerConflictResolver {

    private static final Logger LOGGER = EmakiLog.of("EmakiCoreLib");

    private final Map<String, Set<String>> conflictMatrix = new HashMap<>();

    public void buildFromDefinitions(Map<String, TriggerDefinition> definitions) {
        conflictMatrix.clear();

        for (String id : definitions.keySet()) {
            conflictMatrix.computeIfAbsent(id, k -> new HashSet<>()).add(id);
        }

        for (TriggerDefinition def : definitions.values()) {
            for (String other : def.incompatibleWith()) {
                if (!definitions.containsKey(other)) {
                    LOGGER.warning("[trigger] 触发器 '" + def.id()
                            + "' 声明了与未知触发器 '" + other + "' 的不兼容");
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
