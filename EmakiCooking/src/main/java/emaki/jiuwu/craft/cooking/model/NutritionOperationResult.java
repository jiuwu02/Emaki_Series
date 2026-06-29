package emaki.jiuwu.craft.cooking.model;

/**
 * 营养增/减/设置操作的结果。
 *
 * @param success 操作是否成功
 * @param typeId  营养类型 id
 * @param oldValue 操作前的值
 * @param newValue 操作后的值（已截断）
 * @param reason  失败原因（成功时为空字符串）
 */
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
