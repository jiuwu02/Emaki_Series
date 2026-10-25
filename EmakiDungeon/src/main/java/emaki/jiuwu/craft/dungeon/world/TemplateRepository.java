package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

public final class TemplateRepository {

    private final JavaPlugin plugin;
    private final File templateRoot;

    public TemplateRepository(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.templateRoot = new File(dataFolder, "world");
    }

    public File templateRoot() {
        return templateRoot;
    }

    public List<String> discoverDungeons() {
        File[] children = templateRoot.listFiles(File::isDirectory);
        if (children == null) {
            return List.of();
        }
        List<String> dungeons = new ArrayList<>();
        for (File child : children) {
            if (new File(child, "config.yml").isFile()) {
                dungeons.add(child.getName());
            }
        }
        return dungeons;
    }

    public Optional<File> templateDir(String dungeonId) {
        File dir = new File(templateRoot, dungeonId);
        if (dir.isDirectory() && new File(dir, "config.yml").isFile()) {
            return Optional.of(dir);
        }
        return Optional.empty();
    }

    public DungeonConfig loadConfig(String dungeonId) {
        File dir = new File(templateRoot, dungeonId);
        File configFile = new File(dir, "config.yml");
        YamlSection section = YamlFiles.load(configFile);
        if (section == null) {
            throw new IllegalArgumentException("config.yml missing: " + dungeonId);
        }
        return DungeonConfig.parse(section);
    }

    public @Nullable DungeonConfig findConfig(String dungeonId) {
        if (templateDir(dungeonId).isEmpty()) {
            return null;
        }
        try {
            return loadConfig(dungeonId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public File ensureFlowFile(String dungeonId) throws IOException {
        File flowFile = new File(new File(templateRoot, dungeonId), "flow.yml");
        if (!flowFile.isFile()) {
            YamlFiles.copyResourceIfMissing(
                    plugin, "world/flow.yml", flowFile);
        }
        return flowFile;
    }

    public void ensureDefaultConfigs() {
        File[] children = templateRoot.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            File configFile = new File(child, "config.yml");
            if (configFile.isFile()) {
                continue;
            }
            try {
                YamlFiles.copyResourceIfMissing(
                        plugin, "world/default_config.yml", configFile);
            } catch (IOException exception) {
                plugin.getLogger().warning("[EmakiDungeon] default_config 复制失败 | 副本="
                        + child.getName() + " | 错误=" + exception.getMessage());
            }
        }
    }
}
