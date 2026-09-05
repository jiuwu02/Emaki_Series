package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;

final class MaterialIdentityPlanner {

    private MaterialIdentityPlanner() {
    }

    static Plan plan(List<Definition> definitions, List<Input> inputs, BiPredicate<Definition, Input> matcher) {
        List<Definition> ordered = new ArrayList<>(definitions == null ? List.of() : definitions);
        ordered.sort(Comparator.comparing(Definition::matcherConstrained).reversed()
                .thenComparingInt(Definition::order));
        Map<Integer, Integer> remainingByInput = new LinkedHashMap<>();
        for (Input input : inputs == null ? List.<Input>of() : inputs) {
            remainingByInput.put(input.index(), Math.max(0, input.amount()));
        }
        List<Allocation> allocations = new ArrayList<>();
        Map<String, Integer> consumedByCountKey = new LinkedHashMap<>();
        for (Definition definition : ordered) {
            int required = definition.amount() < 0 ? 1 : Math.max(1, definition.amount());
            int remaining = required;
            for (Input input : inputs == null ? List.<Input>of() : inputs) {
                int available = remainingByInput.getOrDefault(input.index(), 0);
                if (available <= 0 || remaining <= 0 || !matcher.test(definition, input)) {
                    continue;
                }
                int assigned = Math.min(available, remaining);
                int consumed = definition.protection() || definition.amount() < 0 ? 0 : assigned;
                allocations.add(new Allocation(definition.order(), definition.materialId(), definition.countKey(),
                        input.index(), assigned, consumed,
                        definition.protection(), definition.temperBoost()));
                remainingByInput.put(input.index(), available - assigned);
                if (consumed > 0) {
                    consumedByCountKey.merge(definition.countKey(), consumed, Integer::sum);
                }
                remaining -= assigned;
            }
            if (remaining > 0 && !definition.optional()) {
                return new Plan(false, List.copyOf(allocations), Map.copyOf(consumedByCountKey));
            }
        }
        return new Plan(true, List.copyOf(allocations), Map.copyOf(consumedByCountKey));
    }

    record Definition(int order,
            String materialId,
            String countKey,
            int amount,
            boolean optional,
            boolean protection,
            int temperBoost,
            boolean matcherConstrained) {
        Definition {
            materialId = normalize(materialId);
            countKey = normalize(countKey).isEmpty() ? materialId : normalize(countKey);
        }
    }

    record Input(int index, int amount) {
        Input {
            amount = Math.max(0, amount);
        }
    }

    record Allocation(int definitionOrder,
            String materialId,
            String countKey,
            int inputIndex,
            int assigned,
            int consumed,
            boolean protection,
            int temperBoost) {
    }

    record Plan(boolean satisfied, List<Allocation> allocations, Map<String, Integer> consumedByCountKey) {
        int consumedForMaterial(String materialId) {
            int total = 0;
            String normalized = normalize(materialId);
            for (Allocation allocation : allocations) {
                if (allocation.materialId().equals(normalized)) {
                    total += allocation.consumed();
                }
            }
            return total;
        }

        int consumedFromInput(int inputIndex) {
            int total = 0;
            for (Allocation allocation : allocations) {
                if (allocation.inputIndex() == inputIndex) {
                    total += allocation.consumed();
                }
            }
            return total;
        }

        boolean protectionApplied() {
            for (Allocation allocation : allocations) {
                if (allocation.protection() && allocation.assigned() > 0) {
                    return true;
                }
            }
            return false;
        }

        int temperBonus() {
            int total = 0;
            for (Allocation allocation : allocations) {
                total += allocation.consumed() * Math.max(0, allocation.temperBoost());
            }
            return total;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
