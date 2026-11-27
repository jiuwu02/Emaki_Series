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

    /** 默认环路比例, 与旧硬编码 floor(count * 0.15) 等价。 */
    private static final int DEFAULT_MAX_LOOPS_PERCENT = 15;

    /**
     * 生成枝杈状随机树 (原行为)。
     *
     * <p>从原点向外随机挑前沿边生长, 结果必然出现分叉节点; 适合有多个门、需要多次分叉的房型。
     */
    public Topology generate(long seed, int roomCount) {
        return generateBranch(seed, roomCount, DEFAULT_MAX_LOOPS_PERCENT);
    }

    /** 兼容旧调用: 显式指定环路比例。 */
    public Topology generate(long seed, int roomCount, int maxLoopsPercent) {
        return generateBranch(seed, roomCount, maxLoopsPercent);
    }

    /**
     * 按配置策略生成拓扑。
     *
     * @param style           branch | chain | mixed
     * @param chainChance     mixed 时走 chain 的概率百分比
     * @param maxLoopsPercent 环路数上限占房量百分比, 0 表示纯树
     */
    public Topology generate(long seed, int roomCount, String style, int chainChance,
            int maxLoopsPercent) {
        if ("chain".equals(style)) {
            return generateChain(seed, roomCount, maxLoopsPercent);
        }
        if ("mixed".equals(style)) {
            Random picker = new Random(seed ^ 0x5DEECE66DL);
            if (picker.nextInt(100) < chainChance) {
                return generateChain(seed, roomCount, maxLoopsPercent);
            }
        }
        return generateBranch(seed, roomCount, maxLoopsPercent);
    }

    private Topology generateBranch(long seed, int roomCount, int maxLoopsPercent) {
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
        int maxLoops = (int) Math.floor(count * (Math.max(0, maxLoopsPercent) / 100.0D));
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

    /**
     * 生成蛇形 (boustrophedon) 链状拓扑。
     *
     * <p>按行来回扫描铺满网格: 第 0 行从左到右, 第 1 行从右到左, 依此类推, 相邻行在端头用一格
     * 竖直移动连接。这样全部节点串成一条不与自身相邻的链, 只有两个叶节点, 其余节点度数恰好 2。
     * 门锚点少的房型 (例如只在墙上开一个门的出生房) 在这种拓扑下不会被要求接第三条连接。
     *
     * <p>环路只在链的两端之间追加一条边 (闭合), 不改变中间节点的度数。
     */
    private Topology generateChain(long seed, int roomCount, int maxLoopsPercent) {
        int count = Math.max(2, roomCount);
        Random random = new Random(seed);
        int width = (int) Math.ceil(Math.sqrt(count));
        if (width < 2) {
            width = 2;
        }
        List<Node> nodes = new ArrayList<>(count);
        Map<Long, Integer> cellIndex = new HashMap<>();
        List<Edge> edges = new ArrayList<>();
        int placed = 0;
        int previousId = -1;
        for (int row = 0; placed < count; row++) {
            boolean leftToRight = (row % 2) == 0;
            for (int column = 0; column < width && placed < count; column++) {
                int gridX = leftToRight ? column : width - 1 - column;
                int id = addNode(nodes, cellIndex, gridX, row);
                if (previousId >= 0) {
                    edges.add(new Edge(previousId, id, true));
                }
                previousId = id;
                placed++;
            }
        }
        // 链式拓扑默认保持开口 (两个叶节点): 只有 1 个门的房型必须有叶位可放。
        // 首尾闭合会让两端从度 1 变成度 2, 叶节点归零, 因此仅在 max-loops-percent >= 100
        // (显式要求成环) 时才闭合, 且不消耗默认的 15% 预算。
        if (maxLoopsPercent >= 100 && nodes.size() >= 4) {
            edges.add(new Edge(0, nodes.size() - 1, false));
        }
        Map<Integer, Integer> depths = new Topology(List.copyOf(nodes), List.copyOf(edges), 0, 0, "")
                .depths();
        int deepest = nodes.size() - 1;
        int deepestDepth = -1;
        for (Map.Entry<Integer, Integer> entry : depths.entrySet()) {
            if (entry.getValue() > deepestDepth) {
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
