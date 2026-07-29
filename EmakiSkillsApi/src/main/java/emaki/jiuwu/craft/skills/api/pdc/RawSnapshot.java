package emaki.jiuwu.craft.skills.api.pdc;

import java.util.LinkedHashMap;
import java.util.Map;

public record RawSnapshot(
        String skillIds,
        String activeSlot,
        String boundTriggers) {

    private static final RawSnapshot EMPTY = new RawSnapshot(null, null, null);

    public static RawSnapshot empty() {
        return EMPTY;
    }

    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        if (skillIds != null) {
            values.put("skill_ids", skillIds);
        }
        if (activeSlot != null) {
            values.put("active_slot", activeSlot);
        }
        if (boundTriggers != null) {
            values.put("skill_triggers", boundTriggers);
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }
}
