package emaki.jiuwu.craft.skills.model;

public record SkillResourceCost(ResourceCostType type,
        String targetId,
        double amount,
        CostOperation operation,
        String failureMessage) {

    public SkillResourceCost {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        targetId = targetId == null ? "" : targetId;
        amount = Math.max(0D, amount);
        operation = operation == null ? CostOperation.CONSUME : operation;
    }
}
