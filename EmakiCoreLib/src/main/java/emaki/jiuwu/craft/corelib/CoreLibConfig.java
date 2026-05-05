package emaki.jiuwu.craft.corelib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record CoreLibConfig(Map<String, List<String>> actionTemplates, ScriptConfig scriptConfig) {

    public static CoreLibConfig defaults() {
        return new CoreLibConfig(Map.of(), ScriptConfig.defaults());
    }

    public static CoreLibConfig fromConfig(YamlSection configuration) {
        if (configuration == null) {
            return defaults();
        }
        YamlSection actionSection = configuration.getSection("action");
        YamlSection templatesSection = actionSection == null ? null : actionSection.getSection("templates");
        Map<String, List<String>> templates = new LinkedHashMap<>();
        if (templatesSection != null) {
            for (String key : templatesSection.getKeys(false)) {
                templates.put(key, List.copyOf(templatesSection.getStringList(key)));
            }
        }
        return new CoreLibConfig(Map.copyOf(templates), ScriptConfig.fromConfig(configuration.getSection("script")));
    }
}
