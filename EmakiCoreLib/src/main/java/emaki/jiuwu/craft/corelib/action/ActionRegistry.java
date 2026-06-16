package emaki.jiuwu.craft.corelib.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionRegistry {

    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, String> owners = new LinkedHashMap<>();
    private final Map<String, String> sources = new LinkedHashMap<>();

    public ActionResult register(Action action) {
        return register(null, "", action);
    }

    public ActionResult register(Plugin owner, Action action) {
        return register(owner, "", action);
    }

    public ActionResult register(Plugin owner, String source, Action action) {
        if (action == null || Texts.isBlank(action.id())) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id cannot be blank.");
        }
        String id = Texts.lower(action.id());
        if (actions.containsKey(id)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id already registered: " + id);
        }
        actions.put(id, action);
        owners.put(id, ownerKey(owner));
        sources.put(id, Texts.toStringSafe(source));
        return ActionResult.ok();
    }

    public void unregister(String actionId) {
        String id = Texts.lower(actionId);
        actions.remove(id);
        owners.remove(id);
        sources.remove(id);
    }

    public void unregisterAll(Plugin owner) {
        String key = ownerKey(owner);
        if (Texts.isBlank(key)) {
            return;
        }
        for (String id : List.copyOf(actions.keySet())) {
            if (key.equals(owners.get(id))) {
                unregister(id);
            }
        }
    }

    public void unregisterAllBySource(String source) {
        String normalized = Texts.toStringSafe(source);
        if (Texts.isBlank(normalized)) {
            return;
        }
        for (String id : List.copyOf(actions.keySet())) {
            if (normalized.equals(sources.get(id))) {
                unregister(id);
            }
        }
    }

    public Action get(String actionId) {
        return actions.get(Texts.lower(actionId));
    }

    public String ownerKeyOf(String actionId) {
        return owners.getOrDefault(Texts.lower(actionId), "");
    }

    public String sourceOf(String actionId) {
        return sources.getOrDefault(Texts.lower(actionId), "");
    }

    public List<Action> byCategory(String category) {
        List<Action> result = new ArrayList<>();
        String normalized = Texts.lower(category);
        for (Action action : actions.values()) {
            if (normalized.equals(Texts.lower(action.category()))) {
                result.add(action);
            }
        }
        return result;
    }

    public List<Action> byOwner(Plugin owner) {
        String key = ownerKey(owner);
        if (Texts.isBlank(key)) {
            return List.of();
        }
        List<Action> result = new ArrayList<>();
        for (Map.Entry<String, Action> entry : actions.entrySet()) {
            if (key.equals(owners.get(entry.getKey()))) {
                result.add(entry.getValue());
            }
        }
        return List.copyOf(result);
    }

    public List<Action> bySource(String source) {
        String normalized = Texts.toStringSafe(source);
        if (Texts.isBlank(normalized)) {
            return List.of();
        }
        List<Action> result = new ArrayList<>();
        for (Map.Entry<String, Action> entry : actions.entrySet()) {
            if (normalized.equals(sources.get(entry.getKey()))) {
                result.add(entry.getValue());
            }
        }
        return List.copyOf(result);
    }

    public Map<String, Action> all() {
        return Map.copyOf(actions);
    }

    public Map<String, String> owners() {
        return Map.copyOf(owners);
    }

    public Map<String, String> sources() {
        return Map.copyOf(sources);
    }

    private String ownerKey(Plugin owner) {
        return owner == null ? "" : owner.getName();
    }

}
