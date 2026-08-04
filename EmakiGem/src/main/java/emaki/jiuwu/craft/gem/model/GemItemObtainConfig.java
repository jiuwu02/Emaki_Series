package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public record GemItemObtainConfig(List<Map<String, Object>> nameActions,
        List<Map<String, Object>> loreActions,
        List<String> actions) {

    public GemItemObtainConfig {
        nameActions = copyActionMaps(nameActions);
        loreActions = copyActionMaps(loreActions);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static GemItemObtainConfig empty() {
        return new GemItemObtainConfig(List.of(), List.of(), List.of());
    }

    public boolean emptyConfig() {
        return nameActions.isEmpty() && loreActions.isEmpty() && actions.isEmpty();
    }

    public static GemItemObtainConfig fromConfig(Object raw) {
        if (raw == null) {
            return empty();
        }
        return new GemItemObtainConfig(
                toActionList(ConfigNodes.get(raw, "name_actions")),
                toActionList(ConfigNodes.get(raw, "lore_actions")),
                List.copyOf(Texts.asStringList(ConfigNodes.get(raw, "actions")))
        );
    }

    private static List<Map<String, Object>> toActionList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            Object plain = ConfigNodes.toPlainData(entry);
            if (!(plain instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> mapEntry : map.entrySet()) {
                if (mapEntry.getKey() != null) {
                    normalized.put(String.valueOf(mapEntry.getKey()), ConfigNodes.toPlainData(mapEntry.getValue()));
                }
            }
            result.add(normalized);
        }
        return result;
    }

    private static List<Map<String, Object>> copyActionMaps(List<Map<String, Object>> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> action : actions) {
            if (action != null && !action.isEmpty()) {
                copied.add(Map.copyOf(new LinkedHashMap<>(action)));
            }
        }
        return copied.isEmpty() ? List.of() : List.copyOf(copied);
    }
}
