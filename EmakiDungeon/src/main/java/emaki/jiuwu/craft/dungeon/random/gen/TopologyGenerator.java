package emaki.jiuwu.craft.dungeon.random.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import emaki.jiuwu.craft.dungeon.random.gen.Topology.Edge;
import emaki.jiuwu.craft.dungeon.random.gen.Topology.Node;

public final class TopologyGenerator {

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public Topology generate(long seed, int roomCount) {
        int count = Math.max(2, roomCount);
        Random random = new Random(seed);
        Map<Long, Integer> cellIndex = new HashMap<>();
        List<Node> nodes = new ArrayList<>(count);
        List<Edge> edges = new ArrayList<>();
        addNode(nodes, cellIndex, 0, 0);
        List<int[]> frontier = new ArrayList<>();
        Set<Long> frontierKeys = new HashSet<>();
        growFrontier(0, 0, cellIndex, frontier, frontierKeys);
        while (nodes.size() < count) {
            if (frontier.isEmpty()) {
                break;
            }
            int pick = random.nextInt(frontier.size());
            int[] step = frontier.remove(pick);
            frontierKeys.remove(key(step[0], step[1], step[2], step[3]));
            long targetKey = key(step[2], step[3]);
            if (cellIndex.containsKey(targetKey)) {
                continue;
            }
            int parentId = cellIndex.get(key(step[0], step[1]));
            int nodeId = addNode(nodes, cellIndex, step[2], step[3]);
            edges.add(new Edge(parentId, nodeId, true));
            growFrontier(step[2], step[3], cellIndex, frontier, frontierKeys);
        }
        List<Edge> loops = new ArrayList<>();
        Set<Long> treePairs = new HashSet<>();
        for (Edge edge : edges) {
            treePairs.add(pairKey(edge.a(), edge.b()));
        }
        for (Map.Entry<Long, Integer> entry : cellIndex.entrySet()) {
            int nodeId = entry.getValue();
            Node node = nodes.get(nodeId);
            for (int[] step : STEPS) {
                Integer neighborId = cellIndex.get(key(node.gridX() + step[0], node.gridZ() + step[1]));
                if (neighborId == null || neighborId <= nodeId) {
                    continue;
                }
                long pair = pairKey(nodeId, neighborId);
                if (!treePairs.contains(pair)) {
                    loops.add(new Edge(nodeId, neighborId, false));
                }
            }
        }
        Collections.shuffle(loops, random);
        int maxLoops = (int) Math.floor(count * 0.15D);
        for (int i = 0; i < loops.size() && i < maxLoops; i++) {
            edges.add(loops.get(i));
        }
        Topology provisional = new Topology(List.copyOf(nodes), List.copyOf(edges), 0, 0, "");
        Map<Integer, Integer> depths = provisional.depths();
        int deepest = 0;
        int deepestDepth = -1;
        for (Map.Entry<Integer, Integer> entry : depths.entrySet()) {
            if (entry.getValue() > deepestDepth
                    || (entry.getValue() == deepestDepth && entry.getKey() < deepest)) {
                deepest = entry.getKey();
                deepestDepth = entry.getValue();
            }
        }
        String hash = Topology.computeHash(nodes, edges);
        return new Topology(List.copyOf(nodes), List.copyOf(edges), 0, deepest, hash);
    }

    private static void growFrontier(int gridX, int gridZ, Map<Long, Integer> cellIndex,
            List<int[]> frontier, Set<Long> frontierKeys) {
        for (int[] step : STEPS) {
            int nextX = gridX + step[0];
            int nextZ = gridZ + step[1];
            long key = key(gridX, gridZ, nextX, nextZ);
            if (!cellIndex.containsKey(key(nextX, nextZ)) && frontierKeys.add(key)) {
                frontier.add(new int[] {gridX, gridZ, nextX, nextZ});
            }
        }
    }

    private static int addNode(List<Node> nodes, Map<Long, Integer> cellIndex, int gridX, int gridZ) {
        int id = nodes.size();
        nodes.add(new Node(id, gridX, gridZ));
        cellIndex.put(key(gridX, gridZ), id);
        return id;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static long key(int fromX, int fromZ, int toX, int toZ) {
        return key(fromX, fromZ) * 31L + key(toX, toZ);
    }

    private static long pairKey(int a, int b) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        return (low << 32) | high;
    }
}
