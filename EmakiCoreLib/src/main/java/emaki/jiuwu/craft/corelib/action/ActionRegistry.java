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

    public synchronized ActionResult register(Action action) {
        return registerHandle(null, "", action).result();
    }

    public synchronized ActionResult register(Plugin owner, Action action) {
        return registerHandle(owner, "", action).result();
    }

    public synchronized ActionResult register(Plugin owner, String source, Action action) {
        return registerHandle(owner, source, action).result();
    }

    public synchronized ActionRegistration registerHandle(Action action) {
        return registerHandle(null, "", action);
    }

    public synchronized ActionRegistration registerHandle(Plugin owner, Action action) {
        return registerHandle(owner, "", action);
    }

    public synchronized ActionRegistration registerHandle(Plugin owner, String source, Action action) {
        String ownerKey = ownerKey(owner);
        String sourceKey = Texts.toStringSafe(source);
        if (action == null || Texts.isBlank(action.id())) {
            return new ActionRegistration("", ownerKey, sourceKey, false,
                    ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id cannot be blank."));
        }
        String id = Texts.lower(action.id());
        if (actions.containsKey(id)) {
            return new ActionRegistration(id, ownerKey, sourceKey, false,
                    ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id already registered: " + id));
        }
        actions.put(id, action);
        owners.put(id, ownerKey);
        sources.put(id, sourceKey);
        return new ActionRegistration(id, ownerKey, sourceKey, true, ActionResult.ok());
    }

    public synchronized void unregister(String actionId) {
        String id = Texts.lower(actionId);
        actions.remove(id);
        owners.remove(id);
        sources.remove(id);
    }

    public synchronized void unregisterAll(Plugin owner) {
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

    public synchronized void unregisterAllBySource(String source) {
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

    public synchronized Action get(String actionId) {
        return actions.get(Texts.lower(actionId));
    }

    public synchronized String ownerKeyOf(String actionId) {
        return owners.getOrDefault(Texts.lower(actionId), "");
    }

    public synchronized String sourceOf(String actionId) {
        return sources.getOrDefault(Texts.lower(actionId), "");
    }

    public synchronized List<Action> byCategory(String category) {
        List<Action> result = new ArrayList<>();
        String normalized = Texts.lower(category);
        for (Action action : actions.values()) {
            if (normalized.equals(Texts.lower(action.category()))) {
                result.add(action);
            }
        }
        return result;
    }

    public synchronized List<Action> byOwner(Plugin owner) {
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

    public synchronized List<Action> bySource(String source) {
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

    public synchronized Map<String, Action> all() {
        return Map.copyOf(actions);
    }

    public synchronized Map<String, String> owners() {
        return Map.copyOf(owners);
    }

    public synchronized Map<String, String> sources() {
        return Map.copyOf(sources);
    }

    private synchronized boolean unregisterIfMatches(String actionId, String ownerKey, String source) {
        String id = Texts.lower(actionId);
        if (Texts.isBlank(id) || !actions.containsKey(id)) {
            return false;
        }
        if (!Texts.toStringSafe(ownerKey).equals(owners.get(id))) {
            return false;
        }
        if (!Texts.toStringSafe(source).equals(sources.get(id))) {
            return false;
        }
        unregister(id);
        return true;
    }

    private String ownerKey(Plugin owner) {
        return owner == null ? "" : owner.getName();
    }

    public final class ActionRegistration {

        private final String actionId;
        private final String ownerKey;
        private final String source;
        private final boolean registered;
        private final ActionResult result;

        private ActionRegistration(String actionId, String ownerKey, String source, boolean registered, ActionResult result) {
            this.actionId = Texts.lower(actionId);
            this.ownerKey = Texts.toStringSafe(ownerKey);
            this.source = Texts.toStringSafe(source);
            this.registered = registered;
            this.result = result == null ? ActionResult.ok() : result;
        }

        public String actionId() {
            return actionId;
        }

        public String ownerKey() {
            return ownerKey;
        }

        public String source() {
            return source;
        }

        public boolean registered() {
            return registered;
        }

        public ActionResult result() {
            return result;
        }

        public boolean unregister() {
            return registered && unregisterIfMatches(actionId, ownerKey, source);
        }
    }

}
