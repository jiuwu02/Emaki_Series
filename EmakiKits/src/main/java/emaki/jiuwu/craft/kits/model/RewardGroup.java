package emaki.jiuwu.craft.kits.model;

import java.util.List;

public record RewardGroup(String groupId,
                          List<RewardEntry> entries,
                          boolean weighted,
                          int totalWeight,
                          PityRule pity) {

    public RewardGroup {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static RewardGroup fixed(String id, List<RewardEntry> entries) {
        return new RewardGroup(id, entries, false, 0, null);
    }

    public static RewardGroup weighted(String id, List<RewardEntry> entries) {
        int total = entries.stream().mapToInt(RewardEntry::weight).sum();
        return new RewardGroup(id, entries, true, total, null);
    }

    public RewardGroup withPity(PityRule rule) {
        return new RewardGroup(groupId, entries, weighted, totalWeight, rule);
    }

    public boolean valid() {
        if (!weighted) {
            return !entries.isEmpty();
        }
        if (entries.isEmpty() || totalWeight <= 0) {
            return false;
        }
        return entries.stream().allMatch(e -> e.weight() > 0);
    }
}
