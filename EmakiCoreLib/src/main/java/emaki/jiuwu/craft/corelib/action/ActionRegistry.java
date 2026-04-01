package emaki.jiuwu.craft.corelib.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionRegistry {

    private final Map<String, Action> actions = new LinkedHashMap<>();

    public ActionResult register(Action action) {
        if (action == null || Texts.isBlank(action.id())) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id cannot be blank.");
        }
        String id = Texts.lower(action.id());
        if (actions.containsKey(id)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id already registered: " + id);
        }
        actions.put(id, action);
        return ActionResult.ok();
    }

    public void unregister(String actionId) {
        actions.remove(Texts.lower(actionId));
    }

    public Action get(String actionId) {
        return actions.get(Texts.lower(actionId));
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

    public Map<String, Action> all() {
        return Map.copyOf(actions);
    }
}
