package emaki.jiuwu.craft.corelib.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionTemplateRegistry {

    private final Map<String, List<String>> templates = new LinkedHashMap<>();

    public void clear() {
        templates.clear();
    }

    public void register(String id, List<String> lines) {
        if (Texts.isBlank(id) || lines == null) {
            return;
        }
        templates.put(Texts.lower(id), List.copyOf(lines));
    }

    public List<String> get(String id) {
        return templates.get(Texts.lower(id));
    }

    public Map<String, List<String>> all() {
        return Map.copyOf(templates);
    }
}
