package emaki.jiuwu.craft.corelib.web.insight;

import java.util.LinkedHashMap;
import java.util.Map;

public record WebInsightDependencyGraphEdge(
        String from,
        String to,
        String edgeType,
        String moduleId,
        String path,
        String kind,
        String keyPath,
        String snippet
) {

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", safe(from));
        result.put("to", safe(to));
        result.put("edgeType", safe(edgeType));
        result.put("moduleId", safe(moduleId));
        result.put("path", safe(path));
        result.put("kind", safe(kind));
        result.put("keyPath", safe(keyPath));
        result.put("snippet", safe(snippet));
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
