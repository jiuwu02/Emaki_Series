package emaki.jiuwu.craft.corelib.trigger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.log.EmakiLog;

public final class TriggerRegistry {

    private static final Logger LOGGER = EmakiLog.of("EmakiCoreLib");

    private final Map<String, TriggerDefinition> definitions = new LinkedHashMap<>();

    public void register(TriggerDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    public TriggerDefinition get(String id) {
        return definitions.get(id);
    }

    public Map<String, TriggerDefinition> all() {
        return Collections.unmodifiableMap(definitions);
    }

    public boolean isEnabled(String id) {
        TriggerDefinition def = definitions.get(id);
        return def != null && def.enabled();
    }

    public String getDisplayName(String id) {
        TriggerDefinition def = definitions.get(id);
        if (def != null) {
            return def.displayName();
        }
        LOGGER.warning("[trigger] 触发器 '" + id + "' 未注册；回退为 [" + id + "]");
        return "[" + id + "]";
    }

    public void clear() {
        definitions.clear();
    }

    public void loadFromConfig(Map<String, TriggerDefinition> configEntries) {
        for (var entry : configEntries.entrySet()) {
            definitions.put(entry.getKey(), entry.getValue());
        }
    }
}
