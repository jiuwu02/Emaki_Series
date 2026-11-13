package emaki.jiuwu.craft.kits.model;

public record RewardEntry(String id,
                          RewardKind kind,
                          int amount,
                          String source,
                          String action,
                          String nestedKitId,
                          int weight,
                          String permission) {

    public enum RewardKind {
        ITEM,
        MONEY,
        NESTED_KIT,
        ACTION
    }

    public RewardEntry {
        amount = Math.max(1, amount);
        weight = Math.max(0, weight);
        source = source == null ? "" : source;
        action = action == null ? "" : action;
        nestedKitId = nestedKitId == null ? "" : nestedKitId;
        permission = permission == null ? "" : permission;
    }

    public boolean requiresPermission() {
        return !permission.isBlank();
    }
}
