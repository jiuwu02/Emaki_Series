package emaki.jiuwu.craft.dungeon.random.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.LinkRecord;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.RoomPlacement;

public final class TopologyFactory {

    public static Topology fromLayout(Layout layout) {
        List<Topology.Node> nodes = new ArrayList<>();
        for (RoomPlacement room : layout.rooms()) {
            nodes.add(new Topology.Node(room.nodeId(), room.originX(), room.originZ()));
        }
        Set<Integer> known = new HashSet<>();
        known.add(0);
        List<Topology.Edge> edges = new ArrayList<>();
        for (LinkRecord link : layout.links()) {
            edges.add(new Topology.Edge(link.parentNodeId(), link.childNodeId(), known.add(link.childNodeId())));
        }
        Map<Integer, List<Integer>> adjacent = new HashMap<>();
        for (Topology.Edge edge : edges) {
            adjacent.computeIfAbsent(edge.a(), key -> new ArrayList<>()).add(edge.b());
            adjacent.computeIfAbsent(edge.b(), key -> new ArrayList<>()).add(edge.a());
        }
        Map<Integer, Integer> depth = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        depth.put(0, 0);
        queue.add(0);
        int deepest = 0;
        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (depth.get(current) > depth.getOrDefault(deepest, 0)) {
                deepest = current;
            }
            for (int next : adjacent.getOrDefault(current, List.of())) {
                if (!depth.containsKey(next)) {
                    depth.put(next, depth.get(current) + 1);
                    queue.add(next);
                }
            }
        }
        return new Topology(List.copyOf(nodes), List.copyOf(edges), 0, deepest,
                Topology.computeHash(nodes, edges));
    }

    private TopologyFactory() {
    }
}
