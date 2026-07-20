package emaki.jiuwu.craft.cooking.model;

import java.util.List;


















public record NutritionComboThreshold(String id,
        List<String> types,
        double value,
        NutritionCompare compare,
        int requiredCount,
        List<String> onMeetActions,
        List<String> onRecoverActions) {

    public NutritionComboThreshold {
        types = types == null ? List.of() : List.copyOf(types);
        onMeetActions = onMeetActions == null ? List.of() : List.copyOf(onMeetActions);
        onRecoverActions = onRecoverActions == null ? List.of() : List.copyOf(onRecoverActions);
        compare = compare == null ? NutritionCompare.GREATER_OR_EQUAL : compare;
        requiredCount = Math.max(1, requiredCount);
    }




    public boolean counts(String typeId) {
        return types.isEmpty() || types.contains(typeId);
    }
}
