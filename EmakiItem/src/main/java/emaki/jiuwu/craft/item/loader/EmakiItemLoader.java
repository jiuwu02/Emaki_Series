package emaki.jiuwu.craft.item.loader;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.item.loader.migration.configureditem.ConfiguredItemMigrationBridge;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

public final class EmakiItemLoader {

    private final JavaPlugin plugin;
    private final EmakiItemDefinitionParser parser;
    private volatile Map<String, EmakiItemDefinition> definitions = Map.of();

    public EmakiItemLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.parser = new EmakiItemDefinitionParser(plugin.getLogger());
    }

    public int load() {
        File directory = plugin.getDataFolder().toPath().resolve("items").toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create items directory: " + directory.getPath());
        }
        Map<String, EmakiItemDefinition> loaded = new LinkedHashMap<>();
        File[] files = files(directory);
        ConfiguredItemMigrationBridge.migrate(plugin, files);
        for (File file : files) {
            try {
                EmakiItemDefinition definition = parser.parse(YamlFiles.load(file), file.getPath());
                if (definition == null) {
                    continue;
                }
                if (loaded.containsKey(definition.id())) {
                    plugin.getLogger().warning("Duplicate EmakiItem id '" + definition.id() + "' in " + file.getPath() + ", keeping first definition.");
                    continue;
                }
                loaded.put(definition.id(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not load EmakiItem definition " + file.getPath()
                        + ": " + Texts.toStringSafe(exception.getMessage()));
            }
        }
        definitions = new ConcurrentHashMap<>(loaded);
        return definitions.size();
    }

    public EmakiItemDefinition get(String id) {
        return definitions.get(Texts.normalizeId(id));
    }

    public Map<String, EmakiItemDefinition> all() {
        return Map.copyOf(definitions);
    }

    private File[] files(File directory) {
        if (directory == null || !directory.exists()) {
            return new File[0];
        }
        File[] files = directory.listFiles(file -> file.isDirectory()
                || file.getName().endsWith(".yml")
                || file.getName().endsWith(".yaml"));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        java.util.List<File> result = new java.util.ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                result.addAll(Arrays.asList(files(file)));
            } else {
                result.add(file);
            }
        }
        return result.toArray(File[]::new);
    }
}
