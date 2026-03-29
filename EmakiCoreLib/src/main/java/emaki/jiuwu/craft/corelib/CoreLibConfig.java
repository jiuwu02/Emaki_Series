package emaki.jiuwu.craft.corelib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public record CoreLibConfig(Map<String, List<String>> actionTemplates) {

    public static CoreLibConfig defaults() {
        return new CoreLibConfig(Map.of());
    }

    public static CoreLibConfig fromConfig(YamlConfiguration configuration) {
        if (configuration == null) {
            return defaults();
        }
        ConfigurationSection actionSection = configuration.getConfigurationSection("action");
        ConfigurationSection templatesSection = actionSection == null ? null : actionSection.getConfigurationSection("templates");
        Map<String, List<String>> templates = new LinkedHashMap<>();
        if (templatesSection != null) {
            for (String key : templatesSection.getKeys(false)) {
                templates.put(key, List.copyOf(templatesSection.getStringList(key)));
            }
        }
        return new CoreLibConfig(Map.copyOf(templates));
    }
}
