package emaki.jiuwu.craft.corelib.web.insight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WebInsightDependencyGraphService {

    private final WebInsightReferenceService referenceService;

    public WebInsightDependencyGraphService(WebInsightReferenceService referenceService) {
        this.referenceService = referenceService;
    }

    public Map<String, Object> graph(String idType, String id, int depth, String direction) throws IOException {
        String normalizedType = normalize(idType);
        String normalizedId = normalizeReferenceId(normalizedType, id);
        int normalizedDepth = Math.max(1, Math.min(1, depth));
        String normalizedDirection = normalizeDirection(direction);
        Map<String, WebInsightDependencyGraphNode> nodes = new LinkedHashMap<>();
        List<WebInsightDependencyGraphEdge> edges = new ArrayList<>();
        if (normalizedType.isBlank() || normalizedId.isBlank()) {
            return toMap(normalizedType, normalizedId, normalizedDepth, normalizedDirection, List.of(), List.of());
        }

        String rootKey = nodeKey(normalizedType, normalizedId);
        nodes.put(rootKey, new WebInsightDependencyGraphNode(rootKey, normalizedType, normalizedId, normalizedId, "", "", "", "root"));
        if (!"downstream".equals(normalizedDirection)) {
            for (Map<String, Object> reference : referenceService.references(normalizedType, normalizedId)) {
                String moduleId = string(reference.get("moduleId"));
                String path = string(reference.get("path"));
                String kind = string(reference.get("kind"));
                String keyPath = string(reference.get("keyPath"));
                String edgeType = string(reference.get("edgeType"));
                String snippet = string(reference.get("snippet"));
                String fileKey = fileNodeKey(moduleId, path);
                nodes.putIfAbsent(fileKey, new WebInsightDependencyGraphNode(fileKey, "config_file", path, path, moduleId, path, kind, "reference"));
                edges.add(new WebInsightDependencyGraphEdge(fileKey, rootKey, edgeType.isBlank() ? "uses" : edgeType, moduleId, path, kind, keyPath, snippet));
            }
        }
        return toMap(normalizedType, normalizedId, normalizedDepth, normalizedDirection, List.copyOf(nodes.values()), edges);
    }

    private Map<String, Object> toMap(String idType, String id, int depth, String direction, List<WebInsightDependencyGraphNode> nodes, List<WebInsightDependencyGraphEdge> edges) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idType", idType);
        result.put("id", id);
        result.put("depth", depth);
        result.put("direction", direction);
        result.put("nodes", nodes.stream().map(WebInsightDependencyGraphNode::toMap).toList());
        result.put("edges", edges.stream().map(WebInsightDependencyGraphEdge::toMap).toList());
        return result;
    }

    private String normalizeDirection(String direction) {
        String normalized = normalize(direction);
        if ("upstream".equals(normalized) || "downstream".equals(normalized) || "both".equals(normalized)) {
            return normalized;
        }
        return "both";
    }

    private String nodeKey(String idType, String id) {
        return idType + ":" + id;
    }

    private String fileNodeKey(String moduleId, String path) {
        return moduleId + ":" + path;
    }

    private String normalizeReferenceId(String idType, String value) {
        String normalized = normalize(value);
        if ("emaki_item".equals(idType)) {
            if (normalized.startsWith("emakiitem-")) {
                return normalized.substring("emakiitem-".length());
            }
            if (normalized.startsWith("ei-")) {
                return normalized.substring("ei-".length());
            }
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
