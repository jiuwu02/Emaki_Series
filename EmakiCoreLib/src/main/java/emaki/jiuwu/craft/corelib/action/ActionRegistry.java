package emaki.jiuwu.craft.corelib.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionRegistry {

    private final Plugin defaultOwner;
    private final Map<String, RegisteredAction> actions = new LinkedHashMap<>();
    private final AtomicLong generationSequence = new AtomicLong();

    public ActionRegistry() {
        this(null);
    }

    public ActionRegistry(Plugin defaultOwner) {
        this.defaultOwner = defaultOwner;
    }

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
        Plugin effectiveOwner = owner == null ? defaultOwner : owner;
        String ownerKey = ownerKey(effectiveOwner);
        String sourceKey = Texts.toStringSafe(source);
        if (action == null || Texts.isBlank(action.id())) {
            return new ActionRegistration("", ownerKey, sourceKey, -1L, false,
                    ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id cannot be blank."));
        }
        String id = Texts.lower(action.id());
        if (actions.containsKey(id)) {
            return new ActionRegistration(id, ownerKey, sourceKey, -1L, false,
                    ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id already registered: " + id));
        }
        long generation = generationSequence.incrementAndGet();
        actions.put(id, new RegisteredAction(action, effectiveOwner, ownerKey, sourceKey, generation));
        return new ActionRegistration(id, ownerKey, sourceKey, generation, true, ActionResult.ok());
    }

    public synchronized void unregister(String actionId) {
        actions.remove(Texts.lower(actionId));
    }

    public synchronized void unregisterAll(Plugin owner) {
        if (owner == null) {
            return;
        }
        for (String id : List.copyOf(actions.keySet())) {
            RegisteredAction registered = actions.get(id);
            if (registered != null && registered.owner() == owner) {
                actions.remove(id);
            }
        }
    }

    public synchronized void unregisterAllBySource(String source) {
        String normalized = Texts.toStringSafe(source);
        if (Texts.isBlank(normalized)) {
            return;
        }
        for (String id : List.copyOf(actions.keySet())) {
            RegisteredAction registered = actions.get(id);
            if (registered != null && normalized.equals(registered.source())) {
                actions.remove(id);
            }
        }
    }

    public synchronized Action get(String actionId) {
        RegisteredAction registered = getRegistered(actionId);
        return registered == null ? null : registered.action();
    }

    public synchronized RegisteredAction getRegistered(String actionId) {
        return actions.get(Texts.lower(actionId));
    }

    public synchronized String ownerKeyOf(String actionId) {
        RegisteredAction registered = getRegistered(actionId);
        return registered == null ? "" : registered.ownerKey();
    }

    public synchronized String sourceOf(String actionId) {
        RegisteredAction registered = getRegistered(actionId);
        return registered == null ? "" : registered.source();
    }

    public synchronized List<Action> byCategory(String category) {
        List<Action> result = new ArrayList<>();
        String normalized = Texts.lower(category);
        for (RegisteredAction registered : actions.values()) {
            if (normalized.equals(Texts.lower(registered.action().category()))) {
                result.add(registered.action());
            }
        }
        return List.copyOf(result);
    }

    public synchronized List<Action> byOwner(Plugin owner) {
        if (owner == null) {
            return List.of();
        }
        List<Action> result = new ArrayList<>();
        for (RegisteredAction registered : actions.values()) {
            if (registered.owner() == owner) {
                result.add(registered.action());
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
        for (RegisteredAction registered : actions.values()) {
            if (normalized.equals(registered.source())) {
                result.add(registered.action());
            }
        }
        return List.copyOf(result);
    }

    public synchronized Map<String, Action> all() {
        Map<String, Action> result = new LinkedHashMap<>();
        actions.forEach((id, registered) -> result.put(id, registered.action()));
        return Map.copyOf(result);
    }

    public synchronized Map<String, String> owners() {
        Map<String, String> result = new LinkedHashMap<>();
        actions.forEach((id, registered) -> result.put(id, registered.ownerKey()));
        return Map.copyOf(result);
    }

    public synchronized Map<String, String> sources() {
        Map<String, String> result = new LinkedHashMap<>();
        actions.forEach((id, registered) -> result.put(id, registered.source()));
        return Map.copyOf(result);
    }

    private synchronized boolean unregisterIfMatches(String actionId, long generation) {
        String id = Texts.lower(actionId);
        RegisteredAction registered = actions.get(id);
        if (registered == null || registered.generation() != generation) {
            return false;
        }
        actions.remove(id);
        return true;
    }

    private String ownerKey(Plugin owner) {
        return owner == null ? "" : owner.getName();
    }

    public final class ActionRegistration {

        private final String actionId;
        private final String ownerKey;
        private final String source;
        private final long generation;
        private final boolean registered;
        private final ActionResult result;

        private ActionRegistration(String actionId,
                String ownerKey,
                String source,
                long generation,
                boolean registered,
                ActionResult result) {
            this.actionId = Texts.lower(actionId);
            this.ownerKey = Texts.toStringSafe(ownerKey);
            this.source = Texts.toStringSafe(source);
            this.generation = generation;
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
            return registered && unregisterIfMatches(actionId, generation);
        }
    }
}
