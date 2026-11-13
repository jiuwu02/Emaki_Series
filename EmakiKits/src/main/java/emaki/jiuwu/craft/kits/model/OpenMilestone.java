package emaki.jiuwu.craft.kits.model;

import java.util.List;

public record OpenMilestone(int count, List<RewardEntry> rewards) {

    public OpenMilestone {
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }

    public boolean valid() {
        return count > 0;
    }
}
