package emaki.jiuwu.craft.station.material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public final class AllocationEngine {

    public record Assignment(int candidateIndex, int requirementIndex, long amount) {
    }

    public record Result(boolean satisfied, List<Assignment> assignments) {

        public Result {
            assignments = assignments == null ? List.of() : List.copyOf(assignments);
        }
    }

    private AllocationEngine() {
    }

    public static Result allocate(List<Long> candidateAmounts,
            List<Long> requirementAmounts,
            BiPredicate<Integer, Integer> compatible) {
        if (candidateAmounts == null || requirementAmounts == null || compatible == null) {
            return new Result(false, List.of());
        }
        int candidates = candidateAmounts.size();
        int requirements = requirementAmounts.size();
        int source = 0;
        int sink = 1 + candidates + requirements;
        long[][] capacity = new long[sink + 1][sink + 1];
        long required = 0L;
        for (int candidate = 0; candidate < candidates; candidate++) {
            capacity[source][1 + candidate] = positive(candidateAmounts.get(candidate));
        }
        for (int requirement = 0; requirement < requirements; requirement++) {
            long amount = positive(requirementAmounts.get(requirement));
            capacity[1 + candidates + requirement][sink] = amount;
            required = plus(required, amount);
        }
        for (int candidate = 0; candidate < candidates; candidate++) {
            long amount = capacity[source][1 + candidate];
            if (amount <= 0L) {
                continue;
            }
            for (int requirement = 0; requirement < requirements; requirement++) {
                if (compatible.test(candidate, requirement)) {
                    capacity[1 + candidate][1 + candidates + requirement] = amount;
                }
            }
        }
        long[][] flow = new long[sink + 1][sink + 1];
        long total = 0L;
        while (true) {
            int[] parents = path(capacity, flow, source, sink);
            if (parents == null) {
                break;
            }
            long amount = Long.MAX_VALUE;
            for (int node = sink; node != source; node = parents[node]) {
                int previous = parents[node];
                amount = Math.min(amount, capacity[previous][node] - flow[previous][node]);
            }
            for (int node = sink; node != source; node = parents[node]) {
                int previous = parents[node];
                flow[previous][node] += amount;
                flow[node][previous] -= amount;
            }
            total = plus(total, amount);
        }
        List<Assignment> assignments = new ArrayList<>();
        for (int candidate = 0; candidate < candidates; candidate++) {
            for (int requirement = 0; requirement < requirements; requirement++) {
                long amount = flow[1 + candidate][1 + candidates + requirement];
                if (amount > 0L) {
                    assignments.add(new Assignment(candidate, requirement, amount));
                }
            }
        }
        return new Result(total == required, assignments);
    }

    public static Map<String, Long> countByKey(List<String> countKeys, List<Long> amounts) {
        Map<String, Long> counted = new LinkedHashMap<>();
        if (countKeys == null || amounts == null) {
            return counted;
        }
        int limit = Math.min(countKeys.size(), amounts.size());
        for (int index = 0; index < limit; index++) {
            String key = countKeys.get(index);
            long amount = positive(amounts.get(index));
            if (key != null && !key.isBlank() && amount > 0L) {
                counted.merge(key, amount, AllocationEngine::plus);
            }
        }
        return Map.copyOf(counted);
    }

    private static int[] path(long[][] capacity, long[][] flow, int source, int sink) {
        int[] parents = new int[capacity.length];
        boolean[] visited = new boolean[capacity.length];
        java.util.Arrays.fill(parents, -1);
        visited[source] = true;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next = 0; next < capacity.length; next++) {
                if (visited[next] || capacity[current][next] - flow[current][next] <= 0L) {
                    continue;
                }
                parents[next] = current;
                visited[next] = true;
                if (next == sink) {
                    return parents;
                }
                queue.addLast(next);
            }
        }
        return null;
    }

    private static long positive(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static long plus(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
