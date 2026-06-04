package emaki.jiuwu.craft.level.api;

import java.util.Map;

public record LevelOperationResult(boolean success,
        String reason,
        LevelOperationType operationType,
        String typeId,
        int oldLevel,
        int newLevel,
        double oldExp,
        double newExp,
        double amount,
        Map<String, Object> data) {

    public LevelOperationResult {
        reason = reason == null ? "" : reason;
        typeId = typeId == null ? "" : typeId;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static LevelOperationResult success(LevelOperationType operationType,
            String typeId,
            int oldLevel,
            int newLevel,
            double oldExp,
            double newExp,
            double amount) {
        return new LevelOperationResult(true, "success", operationType, typeId, oldLevel, newLevel, oldExp, newExp, amount, Map.of());
    }

    public static LevelOperationResult failure(String reason, LevelOperationType operationType, String typeId) {
        return new LevelOperationResult(false, reason, operationType, typeId, 0, 0, 0D, 0D, 0D, Map.of());
    }

    public LevelOperationResult withData(Map<String, Object> values) {
        return new LevelOperationResult(success, reason, operationType, typeId, oldLevel, newLevel, oldExp, newExp, amount, values);
    }
}
