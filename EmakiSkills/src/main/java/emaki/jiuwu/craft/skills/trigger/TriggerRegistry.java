package emaki.jiuwu.craft.skills.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Central registry that holds all known {@link SkillTriggerDefinition}s.
 */
public final class TriggerRegistry {

    private static final Logger LOGGER = Logger.getLogger(TriggerRegistry.class.getName());

    private final Map<String, SkillTriggerDefinition> definitions = new LinkedHashMap<>();

    /**
     * Register a trigger definition. Overwrites any existing definition with the same id.
     */
    public void register(SkillTriggerDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    /**
     * @return the definition for the given id, or {@code null} if not found
     */
    public SkillTriggerDefinition get(String id) {
        return definitions.get(id);
    }

    /**
     * @return an unmodifiable view of all registered definitions keyed by id
     */
    public Map<String, SkillTriggerDefinition> all() {
        return Collections.unmodifiableMap(definitions);
    }

    /**
     * @return {@code true} if the trigger exists and is enabled
     */
    public boolean isEnabled(String id) {
        SkillTriggerDefinition def = definitions.get(id);
        return def != null && def.enabled();
    }

    /**
     * Returns the display name for the given trigger id.
     * Falls back to {@code "[id]"} with a warning if the trigger is not registered.
     */
    public String getDisplayName(String id) {
        SkillTriggerDefinition def = definitions.get(id);
        if (def != null) {
            return def.displayName();
        }
        LOGGER.warning("Trigger '" + id + "' is not registered; falling back to [" + id + "]");
        return "[" + id + "]";
    }

    /**
     * Remove all registered definitions.
     */
    public void clear() {
        definitions.clear();
    }

    /**
     * Populate the registry from a config-style map. Each entry maps a trigger id
     * to its {@link SkillTriggerDefinition}. Existing definitions are replaced.
     */
    public void loadFromConfig(Map<String, SkillTriggerDefinition> configEntries) {
        for (var entry : configEntries.entrySet()) {
            definitions.put(entry.getKey(), entry.getValue());
        }
    }

    // ------------------------------------------------------------------
    // Built-in defaults
    // ------------------------------------------------------------------

    /**
     * @return the 15 built-in trigger definitions with default display names
     */
    public static List<SkillTriggerDefinition> defaultDefinitions() {
        List<SkillTriggerDefinition> defs = new ArrayList<>();

        defs.add(simple("left_click", "[左键]"));
        defs.add(simple("right_click", "[右键]"));
        defs.add(simple("shift_left_click", "[Shift + 左键]"));
        defs.add(simple("shift_right_click", "[Shift + 右键]"));
        defs.add(simple("drop_q", "[Q 键]"));

        for (int i = 1; i <= 9; i++) {
            defs.add(simple("hotbar_" + i, "[数字键 " + i + "]"));
        }

        // Shift-click triggers conflict with their non-shift counterparts
        // and left/right click conflict with their shift variants.
        return Collections.unmodifiableList(defs);
    }

    private static SkillTriggerDefinition simple(String id, String displayName) {
        return new SkillTriggerDefinition(id, displayName, null, true, Set.of(), null);
    }
}
