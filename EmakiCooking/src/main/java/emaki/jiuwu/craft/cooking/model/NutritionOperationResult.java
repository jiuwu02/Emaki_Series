package emaki.jiuwu.craft.cooking.model;










public record NutritionOperationResult(boolean success,
        String typeId,
        double oldValue,
        double newValue,
        String reason) {

    public static NutritionOperationResult ok(String typeId, double oldValue, double newValue) {
        return new NutritionOperationResult(true, typeId, oldValue, newValue, "");
    }

    public static NutritionOperationResult failure(String typeId, String reason) {
        return new NutritionOperationResult(false, typeId, 0D, 0D, reason);
    }
}
