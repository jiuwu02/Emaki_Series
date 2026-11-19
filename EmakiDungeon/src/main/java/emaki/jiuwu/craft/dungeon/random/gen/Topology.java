package emaki.jiuwu.craft.dungeon.random.gen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Topology(List<Node> nodes, List<Edge> edges, int root, int deepest, String hash) {

    public record Node(int id, int gridX, int gridZ) {
    }

    public record Edge(int a, int b, boolean tree) {

        public boolean touches(int nodeId) {
            return a == nodeId || b == nodeId;
        }

        public int other(int nodeId) {
            return a == nodeId ? b : a;
        }
    }

    public Map<Integer, Integer> depths() {
        Map<Integer, List<Integer>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.a(), key -> new ArrayList<>()).add(edge.b());
            adjacency.computeIfAbsent(edge.b(), key -> new ArrayList<>()).add(edge.a());
        }
        Map<Integer, Integer> depths = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        depths.put(root, 0);
        queue.add(root);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int neighbor : adjacency.getOrDefault(current, List.of())) {
                if (!depths.containsKey(neighbor)) {
                    depths.put(neighbor, depths.get(current) + 1);
                    queue.add(neighbor);
                }
            }
        }
        return depths;
    }

    public static String computeHash(List<Node> nodes, List<Edge> edges) {
        List<String> nodeParts = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            nodeParts.add(node.id() + "@" + node.gridX() + "," + node.gridZ());
        }
        List<String> edgeParts = new ArrayList<>(edges.size());
        for (Edge edge : edges) {
            edgeParts.add(Math.min(edge.a(), edge.b()) + "-" + Math.max(edge.a(), edge.b()) + (edge.tree() ? "t" : "l"));
        }
        Collections.sort(nodeParts);
        Collections.sort(edgeParts);
        String canonical = String.join(";", nodeParts) + "|" + String.join(";", edgeParts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
                builder.append(Character.forDigit(bytes[i] & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(canonical.hashCode());
        }
    }
}
