package emaki.jiuwu.craft.corelib.assembly;

import java.util.Map;

/**
 * Processor interface for a single name modification operation (replace, prepend_prefix, append_suffix, regex_replace).
 */
public interface NameOperationProcessor {

    void process(LocalNameState state, Context context);

    record Context(Map<String, Object> operation, String value, Map<String, Object> variables) {
    }
}
