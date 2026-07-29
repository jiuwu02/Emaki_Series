package emaki.jiuwu.craft.item.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;
import emaki.jiuwu.craft.item.model.ItemDirectoryConfig;

public final class EmakiItemLoader {

    private final JavaPlugin plugin;
    private final EmakiItemDefinitionParser parser;
    private final Supplier<AppConfig> configSupplier;
    private volatile Snapshot snapshot = new Snapshot(0L, Map.of());

    public EmakiItemLoader(JavaPlugin plugin) {
        this(plugin, null);
    }

    public EmakiItemLoader(JavaPlugin plugin, Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.parser = new EmakiItemDefinitionParser(plugin.getLogger());
        this.configSupplier = configSupplier;
    }

    public int load() {
        return load(() -> true);
    }

    public int load(BooleanSupplier allowed) {
        if (!allowed(allowed)) {
            return snapshot.definitions().size();
        }
        File directory = plugin.getDataFolder().toPath().resolve("items").toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create items directory: " + directory.getPath());
        }
        Map<String, EmakiItemDefinition> loaded = new LinkedHashMap<>();
        File[] files = files(directory);
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
        if (!allowed(allowed)) {
            return snapshot.definitions().size();
        }
        install(loaded);
        return loaded.size();
    }

    public EmakiItemDefinition get(String id) {
        return snapshot.definitions().get(Texts.normalizeId(id));
    }

    public Map<String, EmakiItemDefinition> all() {
        return snapshot.definitions();
    }

    public long generation() {
        return snapshot.generation();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private boolean allowed(BooleanSupplier allowed) {
        return allowed == null || allowed.getAsBoolean();
    }

    private synchronized void install(Map<String, EmakiItemDefinition> loaded) {
        Snapshot current = snapshot;
        snapshot = new Snapshot(current.generation() + 1L, loaded);
    }

    private File[] files(File directory) {
        if (directory == null || !directory.exists()) {
            return new File[0];
        }
        List<File> result = new ArrayList<>();
        collect(directory, directory, 1, maxDepth(), result);
        result.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        return result.toArray(File[]::new);
    }

    private void collect(File directory, File root, int depth, int maxDepth, List<File> sink) {
        File[] entries = directory.listFiles(file -> file.isDirectory()
                || file.getName().endsWith(".yml")
                || file.getName().endsWith(".yaml"));
        if (entries == null) {
            return;
        }
        Arrays.sort(entries, (left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        for (File entry : entries) {
            if (!entry.isDirectory()) {
                sink.add(entry);
                continue;
            }
            if (depth >= maxDepth) {
                plugin.getLogger().warning("Skipping EmakiItem directory " + relativize(entry, root)
                        + ": nesting exceeds data_directories.max_depth=" + maxDepth + ".");
                continue;
            }
            collect(entry, root, depth + 1, maxDepth, sink);
        }
    }

    private String relativize(File file, File root) {
        String path = file.getPath();
        String prefix = root.getPath();
        return path.startsWith(prefix) ? root.getName() + path.substring(prefix.length()) : path;
    }

    private int maxDepth() {
        AppConfig config = configSupplier == null ? null : configSupplier.get();
        return config == null ? ItemDirectoryConfig.DEFAULT_MAX_DEPTH : config.directories().maxDepth();
    }

    public record Snapshot(long generation, Map<String, EmakiItemDefinition> definitions) {

        public Snapshot {
            generation = Math.max(0L, generation);
            definitions = definitions == null || definitions.isEmpty() ? Map.of() : Map.copyOf(definitions);
        }

        public EmakiItemDefinition get(String id) {
            return definitions.get(Texts.normalizeId(id));
        }
    }
}
