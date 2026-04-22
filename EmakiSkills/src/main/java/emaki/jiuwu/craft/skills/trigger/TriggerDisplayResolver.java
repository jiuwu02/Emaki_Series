package emaki.jiuwu.craft.skills.trigger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for resolving a trigger id to its human-readable display name.
 * Resolution order:
 * <ol>
 *   <li>Definition's own {@code displayName} (if present in the supplied map)</li>
 *   <li>{@link #DEFAULT_DISPLAY_NAMES} built-in fallback</li>
 *   <li>{@code "[triggerId]"} as last resort</li>
 * </ol>
 */
public final class TriggerDisplayResolver {

    /**
     * The 15 built-in trigger display names.
     */
    public static final Map<String, String> DEFAULT_DISPLAY_NAMES;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("left_click", "[左键]");
        map.put("right_click", "[右键]");
        map.put("shift_left_click", "[Shift + 左键]");
        map.put("shift_right_click", "[Shift + 右键]");
        map.put("drop_q", "[Q 键]");
        for (int i = 1; i <= 9; i++) {
            map.put("hotbar_" + i, "[数字键 " + i + "]");
        }
        DEFAULT_DISPLAY_NAMES = Collections.unmodifiableMap(map);
    }

    private TriggerDisplayResolver() {
        // utility class
    }

    /**
     * Resolve the display name for a trigger id.
     *
     * @param triggerId   the trigger id to resolve
     * @param definitions the currently registered definitions (may be empty)
     * @return the resolved display name, never {@code null}
     */
    public static String resolve(String triggerId, Map<String, SkillTriggerDefinition> definitions) {
        // 1. Try definition
        SkillTriggerDefinition def = definitions.get(triggerId);
        if (def != null) {
            return def.displayName();
        }

        // 2. Try built-in defaults
        String fallback = DEFAULT_DISPLAY_NAMES.get(triggerId);
        if (fallback != null) {
            return fallback;
        }

        // 3. Last resort
        return "[" + triggerId + "]";
    }
}
