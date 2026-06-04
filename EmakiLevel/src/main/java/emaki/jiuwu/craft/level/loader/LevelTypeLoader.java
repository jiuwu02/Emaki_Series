package emaki.jiuwu.craft.level.loader;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;

public final class LevelTypeLoader {

    private final JavaPlugin plugin;
    private Map<String, LevelTypeConfig> types = Map.of();

    public LevelTypeLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(AppConfig config) {
        File directory = plugin.getDataFolder().toPath().resolve("types").toFile();
        Map<String, LevelTypeConfig> loaded = new LinkedHashMap<>();
        File[] files = directory.listFiles((_, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                YamlSection section = YamlFiles.load(file);
                String fallbackId = file.getName().substring(0, file.getName().length() - 4);
                LevelTypeConfig type = LevelTypeConfig.parse(section, fallbackId, config.defaultStartLevel(), config.defaultMaxLevel());
                if (Texts.isNotBlank(type.id())) {
                    loaded.put(type.id(), type);
                }
            }
        }
        types = Map.copyOf(loaded);
    }

    public Map<String, LevelTypeConfig> types() {
        return types;
    }
}
