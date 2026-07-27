package emaki.jiuwu.craft.attribute.script;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.ScriptWorkerBoundary;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ScriptAttributeDtoMapper {

    private ScriptAttributeDtoMapper() {
    }

    static Map<String, Object> payloadToMap(Object payload) {
        if (payload == null) {
            return null;
        }
        if (ScriptWorkerBoundary.active()) {
            return Map.of();
        }
        if (payload instanceof Map<?, ?> map) {
            return ScriptSnapshots.immutableMap(map);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sourceId", ScriptServiceApiSupport.invoke(payload, "sourceId", new Class<?>[0]));
        map.put("attributes", copyMap(ScriptServiceApiSupport.invoke(payload, "attributes", new Class<?>[0])));
        map.put("meta", copyMap(ScriptServiceApiSupport.invoke(payload, "meta", new Class<?>[0])));
        map.put("conditions", copyMap(ScriptServiceApiSupport.invoke(payload, "conditions", new Class<?>[0])));
        map.put("schemaVersion", ScriptServiceApiSupport.invoke(payload, "schemaVersion", new Class<?>[0]));
        map.put("updatedAt", ScriptServiceApiSupport.invoke(payload, "updatedAt", new Class<?>[0]));
        return ScriptSnapshots.immutableMap(map);
    }

    static Map<String, Object> payloadsToMap(Object payloads) {
        if (ScriptWorkerBoundary.active() || !(payloads instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(Texts.toStringSafe(entry.getKey()), payloadToMap(entry.getValue()));
        }
        return ScriptSnapshots.immutableMap(result);
    }

    static Map<String, Object> damageResultToMap(Object result) {
        if (result == null || ScriptWorkerBoundary.active()) {
            return Map.of();
        }
        if (result instanceof Map<?, ?> map) {
            return ScriptSnapshots.immutableMap(map);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "damageTypeId", ScriptServiceApiSupport.invoke(result, "damageTypeId", new Class<?>[0]));
        put(map, "finalDamage", ScriptServiceApiSupport.invoke(result, "finalDamage", new Class<?>[0]));
        put(map, "critical", ScriptServiceApiSupport.invoke(result, "critical", new Class<?>[0]));
        put(map, "roll", ScriptServiceApiSupport.invoke(result, "roll", new Class<?>[0]));
        put(map, "stageValues", copyMap(ScriptServiceApiSupport.invoke(result, "stageValues", new Class<?>[0])));
        put(map, "context", copyMap(ScriptServiceApiSupport.invoke(result, "context", new Class<?>[0])));
        return ScriptSnapshots.immutableMap(map);
    }

    private static Map<String, Object> copyMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(Texts.toStringSafe(entry.getKey()), entry.getValue());
            }
        }
        return ScriptSnapshots.immutableMap(result);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        map.put(key, value == null ? "" : value);
    }
}
