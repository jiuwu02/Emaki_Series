package emaki.jiuwu.craft.kits.model;

import java.util.List;

public record RewardEntry(String id,
                          RewardKind kind,
                          int amount,
                          String command,
                          String itemMaterial,
                          String itemName,
                          List<String> itemLore,
                          String nestedKitId,
                          int weight,
                          String permission) {

    public enum RewardKind {
        COMMAND,
        ITEM,
        MONEY,
        EXP,
        NESTED_KIT
    }

    public RewardEntry {
        amount = Math.max(1, amount);
        weight = Math.max(0, weight);
        itemLore = itemLore == null ? List.of() : List.copyOf(itemLore);
        command = command == null ? "" : command;
        itemMaterial = itemMaterial == null ? "" : itemMaterial;
        itemName = itemName == null ? "" : itemName;
        nestedKitId = nestedKitId == null ? "" : nestedKitId;
        permission = permission == null ? "" : permission;
    }

    public boolean requiresPermission() {
        return !permission.isBlank();
    }
}
