package emaki.jiuwu.craft.forge.service;

import java.util.Map;

interface NameModificationProcessor {

    void process(LocalNameState state, Context context);

    record Context(Map<String, Object> operation, String value, Map<String, Object> variables) {
    }
}
