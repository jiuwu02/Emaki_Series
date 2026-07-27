package emaki.jiuwu.craft.cooking.loader;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;







public final class NutritionTypeLoader {

    private final JavaPlugin plugin;
    private Map<String, NutritionTypeConfig> types = Map.of();

    public NutritionTypeLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File directory = plugin.getDataFolder().toPath().resolve("nutrition").toFile();
        Map<String, NutritionTypeConfig> loaded = new LinkedHashMap<>();
        File[] files = directory.listFiles((_, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                YamlSection section = YamlFiles.load(file);
                String fallbackId = file.getName().substring(0, file.getName().length() - 4);
                NutritionTypeConfig type = NutritionTypeConfig.parse(section, fallbackId);
                if (Texts.isNotBlank(type.id())) {
                    loaded.put(type.id(), type);
                }
            }
        }
        types = Map.copyOf(loaded);
    }

    public Map<String, NutritionTypeConfig> types() {
        return types;
    }
}
