package emaki.jiuwu.craft.cooking.model;

import java.util.List;

public record NutritionSingleThreshold(String id,
        List<String> types,
        double value,
        NutritionCompare compare,
        List<String> onMeetActions,
        List<String> onRecoverActions) {

    public NutritionSingleThreshold {
        types = types == null ? List.of() : List.copyOf(types);
        onMeetActions = onMeetActions == null ? List.of() : List.copyOf(onMeetActions);
        onRecoverActions = onRecoverActions == null ? List.of() : List.copyOf(onRecoverActions);
        compare = compare == null ? NutritionCompare.GREATER_OR_EQUAL : compare;
    }

    public boolean appliesTo(String typeId) {
        return types.isEmpty() || types.contains(typeId);
    }
}
