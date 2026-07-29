package emaki.jiuwu.craft.skills.api.pdc;

import java.util.List;
import java.util.Map;

public record EquipmentSkillPayload(
        List<String> skillIds,
        String activeSlot,
        Map<String, String> boundTriggers) {

    public EquipmentSkillPayload {
        skillIds = skillIds == null || skillIds.isEmpty() ? List.of() : List.copyOf(skillIds);
        activeSlot = activeSlot == null ? "" : activeSlot;
        boundTriggers = boundTriggers == null || boundTriggers.isEmpty() ? Map.of() : Map.copyOf(boundTriggers);
    }

    public boolean empty() {
        return skillIds.isEmpty();
    }
}
