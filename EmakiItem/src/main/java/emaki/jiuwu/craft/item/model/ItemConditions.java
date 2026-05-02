package emaki.jiuwu.craft.item.model;

import java.util.List;

public record ItemConditions(List<String> entries,
        String type,
        int requiredCount,
        boolean invalidAsFailure,
        String denyMessage,
        List<String> denyActions) {

    public ItemConditions {
        entries = entries == null ? List.of() : List.copyOf(entries);
        type = type == null || type.isBlank() ? "all_of" : type;
        requiredCount = Math.max(0, requiredCount);
        denyMessage = denyMessage == null ? "" : denyMessage;
        denyActions = denyActions == null ? List.of() : List.copyOf(denyActions);
    }

    public static ItemConditions empty() {
        return new ItemConditions(List.of(), "all_of", 1, true, "", List.of());
    }

    public boolean configured() {
        return !entries.isEmpty();
    }
}
