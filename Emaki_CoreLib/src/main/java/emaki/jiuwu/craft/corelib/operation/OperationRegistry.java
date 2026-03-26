package emaki.jiuwu.craft.corelib.operation;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OperationRegistry {

    private final Map<String, Operation> operations = new LinkedHashMap<>();

    public OperationResult register(Operation operation) {
        if (operation == null || Texts.isBlank(operation.id())) {
            return OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Operation id cannot be blank.");
        }
        String id = Texts.lower(operation.id());
        if (operations.containsKey(id)) {
            return OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Operation id already registered: " + id);
        }
        operations.put(id, operation);
        return OperationResult.ok();
    }

    public void unregister(String operationId) {
        operations.remove(Texts.lower(operationId));
    }

    public Operation get(String operationId) {
        return operations.get(Texts.lower(operationId));
    }

    public List<Operation> byCategory(String category) {
        List<Operation> result = new ArrayList<>();
        String normalized = Texts.lower(category);
        for (Operation operation : operations.values()) {
            if (normalized.equals(Texts.lower(operation.category()))) {
                result.add(operation);
            }
        }
        return result;
    }

    public Map<String, Operation> all() {
        return Map.copyOf(operations);
    }
}
