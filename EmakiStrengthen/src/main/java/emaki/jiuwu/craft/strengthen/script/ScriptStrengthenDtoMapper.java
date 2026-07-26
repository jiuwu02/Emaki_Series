package emaki.jiuwu.craft.strengthen.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.ScriptWorkerBoundary;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ScriptStrengthenDtoMapper {

    private ScriptStrengthenDtoMapper() {
    }

    static Map<String, Object> strengthenStateToMap(Object state) {
        if (state == null || ScriptWorkerBoundary.active()) {
            return Map.of();
        }
        if (state instanceof Map<?, ?> map) {
            return ScriptSnapshots.immutableMap(map);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "eligible", ScriptServiceApiSupport.invoke(state, "eligible", new Class<?>[0]));
        put(map, "eligibleReason", ScriptServiceApiSupport.invoke(state, "eligibleReason", new Class<?>[0]));
        put(map, "hasLayer", ScriptServiceApiSupport.invoke(state, "hasLayer", new Class<?>[0]));
        put(map, "baseSource", Texts.toStringSafe(ScriptServiceApiSupport.invoke(state, "baseSource", new Class<?>[0])));
        put(map, "baseSourceSignature", ScriptServiceApiSupport.invoke(state, "baseSourceSignature", new Class<?>[0]));
        put(map, "recipeId", ScriptServiceApiSupport.invoke(state, "recipeId", new Class<?>[0]));
        put(map, "currentStar", ScriptServiceApiSupport.invoke(state, "currentStar", new Class<?>[0]));
        put(map, "crackLevel", ScriptServiceApiSupport.invoke(state, "crackLevel", new Class<?>[0]));
        put(map, "milestoneFlags", new ArrayList<>(asCollection(ScriptServiceApiSupport.invoke(state, "milestoneFlags", new Class<?>[0]))));
        put(map, "successCount", ScriptServiceApiSupport.invoke(state, "successCount", new Class<?>[0]));
        put(map, "failureCount", ScriptServiceApiSupport.invoke(state, "failureCount", new Class<?>[0]));
        put(map, "lastAttemptAt", ScriptServiceApiSupport.invoke(state, "lastAttemptAt", new Class<?>[0]));
        put(map, "branchPath", ScriptServiceApiSupport.invoke(state, "branchPath", new Class<?>[0]));
        put(map, "fractureLevel", ScriptServiceApiSupport.invoke(state, "fractureLevel", new Class<?>[0]));
        return ScriptSnapshots.immutableMap(map);
    }

    private static Collection<?> asCollection(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of();
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        map.put(key, value == null ? "" : value);
    }
}
