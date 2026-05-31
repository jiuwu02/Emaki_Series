package emaki.jiuwu.craft.skills.trigger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TriggerDisplayResolver {

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
    }

    public static String resolve(String triggerId, Map<String, SkillTriggerDefinition> definitions) {
        SkillTriggerDefinition def = definitions.get(triggerId);
        if (def != null) {
            return def.displayName();
        }

        String fallback = DEFAULT_DISPLAY_NAMES.get(triggerId);
        if (fallback != null) {
            return fallback;
        }

        return "[" + triggerId + "]";
    }
}
