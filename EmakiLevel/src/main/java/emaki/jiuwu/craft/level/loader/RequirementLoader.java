package emaki.jiuwu.craft.level.loader;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.level.config.RequirementConfig;

public final class RequirementLoader {

    private final JavaPlugin plugin;
    private RequirementConfig config = RequirementConfig.parse(null);

    public RequirementLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = plugin.getDataFolder().toPath().resolve("requirements.yml").toFile();
        config = RequirementConfig.parse(YamlFiles.load(file));
    }

    public RequirementConfig config() {
        return config;
    }
}
