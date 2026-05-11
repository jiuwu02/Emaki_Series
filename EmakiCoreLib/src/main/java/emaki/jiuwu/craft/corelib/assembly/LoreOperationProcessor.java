package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;
import java.util.Map;

/**
 * Processor interface for a single lore operation (append, prepend, insert, replace, delete, regex_replace).
 */
public interface LoreOperationProcessor {

    void process(List<String> lines, Context context);

    record Context(Map<String, Object> operation,
            List<String> content,
            String anchor,
            Map<String, Object> variables) {
    }
}
