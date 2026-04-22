package emaki.jiuwu.craft.skills.trigger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Builds and queries a symmetric conflict matrix that determines which
 * triggers cannot be bound to the same skill slot simultaneously.
 */
public final class TriggerConflictResolver {

    private static final Logger LOGGER = Logger.getLogger(TriggerConflictResolver.class.getName());

    private final Map<String, Set<String>> conflictMatrix = new HashMap<>();

    /**
     * Rebuild the conflict matrix from the given definitions.
     * <ol>
     *   <li>Every trigger conflicts with itself (no duplicate bindings).</li>
     *   <li>Each {@code incompatibleWith} entry creates a bidirectional conflict.</li>
     *   <li>Unknown ids referenced in {@code incompatibleWith} produce a warning.</li>
     * </ol>
     */
    public void buildFromDefinitions(Map<String, SkillTriggerDefinition> definitions) {
        conflictMatrix.clear();

        // Ensure every known trigger has an entry
        for (String id : definitions.keySet()) {
            conflictMatrix.computeIfAbsent(id, k -> new HashSet<>()).add(id); // self-conflict
        }

        // Process incompatibleWith
        for (SkillTriggerDefinition def : definitions.values()) {
            for (String other : def.incompatibleWith()) {
                if (!definitions.containsKey(other)) {
                    LOGGER.warning("Trigger '" + def.id()
                            + "' declares incompatibility with unknown trigger '" + other + "'");
                    continue;
                }
                // Bidirectional
                conflictMatrix.computeIfAbsent(def.id(), k -> new HashSet<>()).add(other);
                conflictMatrix.computeIfAbsent(other, k -> new HashSet<>()).add(def.id());
            }
        }
    }

    /**
     * @return {@code true} if the two trigger ids conflict (including self-conflict)
     */
    public boolean conflicts(String triggerId1, String triggerId2) {
        Set<String> set = conflictMatrix.get(triggerId1);
        return set != null && set.contains(triggerId2);
    }

    /**
     * @return an unmodifiable set of all trigger ids that conflict with the given id,
     *         or an empty set if the id is unknown
     */
    public Set<String> getConflicts(String triggerId) {
        Set<String> set = conflictMatrix.get(triggerId);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /**
     * Clear the entire conflict matrix.
     */
    public void clear() {
        conflictMatrix.clear();
    }
}
