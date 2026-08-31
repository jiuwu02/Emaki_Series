package emaki.jiuwu.craft.corelib.matcher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.item.ItemComponentSnapshotScope;

public final class MaterialAllocator {

    private MaterialAllocator() {
    }

    public static @NotNull MaterialAllocation allocate(@NotNull List<MaterialRequest> requests,
            @NotNull List<ItemStack> stacks,
            @NotNull Function<ItemStack, MatchContext> contextFactory) {
        List<ItemStack> candidates = usableStacks(stacks);
        try (ItemComponentSnapshotScope _ = ItemComponentSnapshotScope.open()) {
            return solve(requests, candidates, contextFactory);
        }
    }

    public static int maxBatch(@NotNull List<MaterialRequest> requests,
            @NotNull List<ItemStack> stacks,
            @NotNull Function<ItemStack, MatchContext> contextFactory,
            int batchCeiling) {
        if (batchCeiling <= 0) {
            return 0;
        }
        List<ItemStack> candidates = usableStacks(stacks);
        try (ItemComponentSnapshotScope _ = ItemComponentSnapshotScope.open()) {
            if (!solve(scale(requests, 1), candidates, contextFactory).satisfied()) {
                return 0;
            }
            int feasible = 1;
            int low = 2;
            int high = batchCeiling;
            while (low <= high) {
                int middle = low + (high - low) / 2;
                if (solve(scale(requests, middle), candidates, contextFactory).satisfied()) {
                    feasible = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            return feasible;
        }
    }

    private static List<MaterialRequest> scale(List<MaterialRequest> requests, int batch) {
        List<MaterialRequest> scaled = new ArrayList<>(requests.size());
        for (MaterialRequest request : requests) {
            scaled.add(new MaterialRequest(request.matcher(), request.quantity() * batch));
        }
        return scaled;
    }

    private static List<ItemStack> usableStacks(List<ItemStack> stacks) {
        List<ItemStack> candidates = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                candidates.add(stack);
            }
        }
        return candidates;
    }

    private static MaterialAllocation solve(List<MaterialRequest> requests,
            List<ItemStack> stacks,
            Function<ItemStack, MatchContext> contextFactory) {
        int requestCount = requests.size();
        int stackCount = stacks.size();
        int required = 0;
        for (MaterialRequest request : requests) {
            required += request.quantity();
        }
        if (required == 0) {
            return MaterialAllocation.success(List.of());
        }

        int source = 0;
        int sink = 1 + stackCount + requestCount;
        int nodeCount = sink + 1;
        int[][] capacity = new int[nodeCount][nodeCount];

        for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
            capacity[source][stackNode(stackIndex)] = stacks.get(stackIndex).getAmount();
        }
        for (int requestIndex = 0; requestIndex < requestCount; requestIndex++) {
            capacity[requestNode(stackCount, requestIndex)][sink] = requests.get(requestIndex).quantity();
        }
        for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
            ItemStack stack = stacks.get(stackIndex);
            MatchContext context = contextFactory.apply(stack);
            if (context == null) {
                continue;
            }
            for (int requestIndex = 0; requestIndex < requestCount; requestIndex++) {
                if (requests.get(requestIndex).quantity() <= 0) {
                    continue;
                }
                if (testMatcher(requests.get(requestIndex).matcher(), context)) {
                    capacity[stackNode(stackIndex)][requestNode(stackCount, requestIndex)] = stack.getAmount();
                }
            }
        }

        int[][] flow = new int[nodeCount][nodeCount];
        int totalFlow = 0;
        while (true) {
            int[] parents = findAugmentingPath(capacity, flow, source, sink, nodeCount);
            if (parents == null) {
                break;
            }
            int bottleneck = Integer.MAX_VALUE;
            for (int node = sink; node != source; node = parents[node]) {
                int previous = parents[node];
                bottleneck = Math.min(bottleneck, capacity[previous][node] - flow[previous][node]);
            }
            for (int node = sink; node != source; node = parents[node]) {
                int previous = parents[node];
                flow[previous][node] += bottleneck;
                flow[node][previous] -= bottleneck;
            }
            totalFlow += bottleneck;
        }

        List<MaterialAllocation.Assignment> assignments = new ArrayList<>();
        for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
            for (int requestIndex = 0; requestIndex < requestCount; requestIndex++) {
                int amount = flow[stackNode(stackIndex)][requestNode(stackCount, requestIndex)];
                if (amount > 0) {
                    assignments.add(new MaterialAllocation.Assignment(requestIndex, stacks.get(stackIndex), amount));
                }
            }
        }
        if (totalFlow == required) {
            return MaterialAllocation.success(assignments);
        }
        List<MaterialAllocation.Shortage> shortages = new ArrayList<>();
        for (int requestIndex = 0; requestIndex < requestCount; requestIndex++) {
            int allocated = flow[requestNode(stackCount, requestIndex)][sink];
            int requestQuantity = requests.get(requestIndex).quantity();
            if (allocated < requestQuantity) {
                shortages.add(new MaterialAllocation.Shortage(requestIndex, requestQuantity, allocated));
            }
        }
        return MaterialAllocation.failure(assignments, shortages);
    }

    private static boolean testMatcher(Matcher matcher, MatchContext context) {
        try {
            return matcher.test(context);
        } catch (RuntimeException exception) {
            ComponentMatcherSupport.LOGGER.warning("Material allocation matcher threw and is treated as no match: "
                    + exception.getClass().getSimpleName());
            return false;
        }
    }

    private static int[] findAugmentingPath(int[][] capacity, int[][] flow, int source, int sink, int nodeCount) {
        int[] parents = new int[nodeCount];
        boolean[] visited = new boolean[nodeCount];
        for (int node = 0; node < nodeCount; node++) {
            parents[node] = -1;
        }
        visited[source] = true;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next = 0; next < nodeCount; next++) {
                if (visited[next] || capacity[current][next] - flow[current][next] <= 0) {
                    continue;
                }
                visited[next] = true;
                parents[next] = current;
                if (next == sink) {
                    return parents;
                }
                queue.add(next);
            }
        }
        return null;
    }

    private static int stackNode(int stackIndex) {
        return 1 + stackIndex;
    }

    private static int requestNode(int stackCount, int requestIndex) {
        return 1 + stackCount + requestIndex;
    }
}
