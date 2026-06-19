package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ConfigPrecheckRegistry {

    private final Map<String, ConfigPrecheckContributor> contributors = new LinkedHashMap<>();

    public void register(ConfigPrecheckContributor contributor) {
        if (contributor == null || Texts.isBlank(contributor.module())) {
            return;
        }
        contributors.put(Texts.lower(contributor.module()), contributor);
    }

    public ConfigPrecheckContributor get(String module) {
        return contributors.get(Texts.lower(module));
    }

    public void unregister(String module) {
        if (Texts.isBlank(module)) {
            return;
        }
        contributors.remove(Texts.lower(module));
    }

    public List<ConfigPrecheckContributor> all() {
        return List.copyOf(contributors.values());
    }

    public List<String> moduleIds() {
        return List.copyOf(contributors.keySet());
    }
}
