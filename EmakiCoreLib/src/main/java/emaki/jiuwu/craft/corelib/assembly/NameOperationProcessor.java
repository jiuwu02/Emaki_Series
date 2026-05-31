package emaki.jiuwu.craft.corelib.assembly;

import java.util.Map;

public interface NameOperationProcessor {

    void process(LocalNameState state, Context context);

    record Context(Map<String, Object> operation, String value, Map<String, Object> variables) {
    }
}
