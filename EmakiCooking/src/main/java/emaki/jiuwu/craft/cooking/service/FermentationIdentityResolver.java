package emaki.jiuwu.craft.cooking.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class FermentationIdentityResolver {

    record Identity(String slotId, String countKey) {
    }

    record MigrationResult(boolean accepted, Map<Integer, Identity> allocations) {

        MigrationResult {
            allocations = allocations == null ? Map.of() : Map.copyOf(allocations);
        }

        boolean shouldWrite() {
            return accepted;
        }
    }

    private FermentationIdentityResolver() {
    }

    static Identity unique(Collection<Identity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Set<Identity> unique = new LinkedHashSet<>(candidates);
        return unique.size() == 1 ? unique.iterator().next() : null;
    }

    static MigrationResult migrate(Map<Integer, ? extends Collection<Identity>> candidatesBySlot) {
        if (candidatesBySlot == null || candidatesBySlot.isEmpty()) {
            return new MigrationResult(false, Map.of());
        }
        Map<Integer, Identity> allocations = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends Collection<Identity>> entry : candidatesBySlot.entrySet()) {
            Identity identity = unique(entry.getValue());
            if (entry.getKey() == null || identity == null || identity.slotId() == null || identity.slotId().isBlank()
                    || identity.countKey() == null || identity.countKey().isBlank()) {
                return new MigrationResult(false, Map.of());
            }
            allocations.put(entry.getKey(), identity);
        }
        if (hasDuplicateSlotIds(allocations.values())) {
            return new MigrationResult(false, Map.of());
        }
        return new MigrationResult(true, allocations);
    }

    static boolean hasDuplicateSlotIds(Collection<Identity> identities) {
        if (identities == null || identities.isEmpty()) {
            return false;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Identity identity : identities) {
            if (identity == null || identity.slotId() == null || identity.slotId().isBlank() || !seen.add(identity.slotId())) {
                return true;
            }
        }
        return false;
    }

    static Map<String, Integer> aggregate(Map<Integer, String> countKeys, Map<Integer, Integer> amounts) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (countKeys == null || countKeys.isEmpty()) {
            return result;
        }
        for (Map.Entry<Integer, String> entry : countKeys.entrySet()) {
            String countKey = entry.getValue();
            if (countKey == null || countKey.isBlank()) {
                continue;
            }
            int amount = amounts == null ? 1 : Math.max(1, amounts.getOrDefault(entry.getKey(), 1));
            result.merge(countKey, amount, Integer::sum);
        }
        return result;
    }
}
