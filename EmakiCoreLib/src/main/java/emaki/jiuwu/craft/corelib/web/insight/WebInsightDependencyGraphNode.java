package emaki.jiuwu.craft.corelib.web.insight;

import java.util.LinkedHashMap;
import java.util.Map;

public record WebInsightDependencyGraphNode(
        String key,
        String idType,
        String id,
        String label,
        String moduleId,
        String path,
        String kind,
        String role
) {

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", safe(key));
        result.put("idType", safe(idType));
        result.put("id", safe(id));
        result.put("label", safe(label));
        result.put("moduleId", safe(moduleId));
        result.put("path", safe(path));
        result.put("kind", safe(kind));
        result.put("role", safe(role));
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
