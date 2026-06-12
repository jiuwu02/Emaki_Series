package emaki.jiuwu.craft.corelib.web.insight;

import java.util.LinkedHashMap;
import java.util.Map;

public record WebInsightReferenceResult(
        String moduleId,
        String path,
        String kind,
        String keyPath,
        String idType,
        String id,
        String referenceValue,
        String edgeType,
        String snippet,
        boolean alias,
        String aliasSourceId,
        String aliasTargetId,
        String aliasIdType
) {

    public WebInsightReferenceResult(String moduleId,
            String path,
            String kind,
            String keyPath,
            String idType,
            String id,
            String referenceValue,
            String edgeType,
            String snippet) {
        this(moduleId, path, kind, keyPath, idType, id, referenceValue, edgeType, snippet, false, "", "", "");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moduleId", safe(moduleId));
        result.put("path", safe(path));
        result.put("kind", safe(kind));
        result.put("keyPath", safe(keyPath));
        result.put("idType", safe(idType));
        result.put("id", safe(id));
        result.put("referenceValue", safe(referenceValue));
        result.put("edgeType", safe(edgeType));
        result.put("snippet", safe(snippet));
        result.put("alias", alias);
        result.put("aliasSourceId", safe(aliasSourceId));
        result.put("aliasTargetId", safe(aliasTargetId));
        result.put("aliasIdType", safe(aliasIdType));
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
