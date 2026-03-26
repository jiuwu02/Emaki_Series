package emaki.jiuwu.craft.corelib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public record CoreLibConfig(boolean operationDebug, Map<String, List<String>> operationTemplates) {

    public static CoreLibConfig defaults() {
        return new CoreLibConfig(false, Map.of());
    }

    public static CoreLibConfig fromConfig(YamlConfiguration configuration) {
        if (configuration == null) {
            return defaults();
        }
        ConfigurationSection operationSection = configuration.getConfigurationSection("operation");
        ConfigurationSection templatesSection = operationSection == null ? null : operationSection.getConfigurationSection("templates");
        Map<String, List<String>> templates = new LinkedHashMap<>();
        if (templatesSection != null) {
            for (String key : templatesSection.getKeys(false)) {
                templates.put(key, List.copyOf(templatesSection.getStringList(key)));
            }
        }
        return new CoreLibConfig(
            operationSection != null && operationSection.getBoolean("debug", false),
            Map.copyOf(templates)
        );
    }
}
