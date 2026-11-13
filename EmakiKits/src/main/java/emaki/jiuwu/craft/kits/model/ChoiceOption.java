package emaki.jiuwu.craft.kits.model;

import java.util.List;

public record ChoiceOption(String optionId,
                           String display,
                           String permission,
                           List<RewardEntry> rewards,
                           String nestedKitId) {

    public ChoiceOption {
        display = display == null ? optionId : display;
        permission = permission == null ? "" : permission;
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        nestedKitId = nestedKitId == null ? "" : nestedKitId;
    }

    public boolean requiresPermission() {
        return !permission.isBlank();
    }
}
