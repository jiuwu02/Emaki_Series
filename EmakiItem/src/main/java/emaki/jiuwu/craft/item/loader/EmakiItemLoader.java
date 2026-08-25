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

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;
import emaki.jiuwu.craft.item.model.ItemDirectoryConfig;

public final class EmakiItemLoader {

    private final JavaPlugin plugin;
    private final EmakiItemDefinitionParser parser;
    private final Supplier<AppConfig> configSupplier;
    private volatile Snapshot snapshot = new Snapshot(0L, Map.of(), Map.of());

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
        Map<String, String> packs = new LinkedHashMap<>();
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
                packs.put(definition.id(), packIdOf(file, directory));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not load EmakiItem definition " + file.getPath()
                        + ": " + Texts.toStringSafe(exception.getMessage()));
            }
        }
        if (!allowed(allowed)) {
            return snapshot.definitions().size();
        }
        install(loaded, packs);
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

    public String packId(String id) {
        return snapshot.packId(id);
    }

    public Map<String, String> packIds() {
        return snapshot.packIds();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private boolean allowed(BooleanSupplier allowed) {
        return allowed == null || allowed.getAsBoolean();
    }

    private synchronized void install(Map<String, EmakiItemDefinition> loaded, Map<String, String> packs) {
        Snapshot current = snapshot;
        snapshot = new Snapshot(current.generation() + 1L, loaded, packs);
    }

    private String packIdOf(File file, File root) {
        File parent = file == null ? null : file.getParentFile();
        if (parent == null || root == null) {
            return "";
        }
        String parentPath = parent.getPath();
        String rootPath = root.getPath();
        if (!parentPath.startsWith(rootPath)) {
            return "";
        }
        String relative = parentPath.substring(rootPath.length()).replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative;
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
                || (isItemYaml(file) && !ItemPackLoader.PACK_FILE_NAME.equalsIgnoreCase(file.getName())));
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

    private boolean isItemYaml(File file) {
        String name = file == null ? "" : file.getName();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private int maxDepth() {
        AppConfig config = configSupplier == null ? null : configSupplier.get();
        return config == null ? ItemDirectoryConfig.DEFAULT_MAX_DEPTH : config.directories().maxDepth();
    }

    public record Snapshot(long generation,
            Map<String, EmakiItemDefinition> definitions,
            Map<String, String> packIds) {

        public Snapshot {
            generation = Math.max(0L, generation);
            definitions = definitions == null || definitions.isEmpty() ? Map.of() : Map.copyOf(definitions);
            packIds = packIds == null || packIds.isEmpty() ? Map.of() : Map.copyOf(packIds);
        }

        public EmakiItemDefinition get(String id) {
            return definitions.get(Texts.normalizeId(id));
        }

        public String packId(String id) {
            return packIds.getOrDefault(Texts.normalizeId(id), "");
        }
    }
}
