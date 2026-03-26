package emaki.jiuwu.craft.corelib.action;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActionRegistry {

    private final Map<String, Action> operations = new LinkedHashMap<>();

    public ActionResult register(Action operation) {
        if (operation == null || Texts.isBlank(operation.id())) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id cannot be blank.");
        }
        String id = Texts.lower(operation.id());
        if (operations.containsKey(id)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Action id already registered: " + id);
        }
        operations.put(id, operation);
        return ActionResult.ok();
    }

    public void unregister(String actionId) {
        operations.remove(Texts.lower(actionId));
    }

    public Action get(String actionId) {
        return operations.get(Texts.lower(actionId));
    }

    public List<Action> byCategory(String category) {
        List<Action> result = new ArrayList<>();
        String normalized = Texts.lower(category);
        for (Action operation : operations.values()) {
            if (normalized.equals(Texts.lower(operation.category()))) {
                result.add(operation);
            }
        }
        return result;
    }

    public Map<String, Action> all() {
        return Map.copyOf(operations);
    }
}
