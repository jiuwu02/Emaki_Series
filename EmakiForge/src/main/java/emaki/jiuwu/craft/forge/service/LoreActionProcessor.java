package emaki.jiuwu.craft.forge.service;

import java.util.List;
import java.util.Map;

interface LoreActionProcessor {

    void process(List<String> lines, Context context);

    record Context(Map<String, Object> operation,
            List<String> content,
            String anchor,
            Map<String, Object> variables) {
    }
}
