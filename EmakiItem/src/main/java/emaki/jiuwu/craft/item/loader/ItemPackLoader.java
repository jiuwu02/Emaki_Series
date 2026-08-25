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
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.model.ItemDirectoryConfig;
import emaki.jiuwu.craft.item.model.ItemPackDefinition;

public final class ItemPackLoader {

    public static final String PACK_FILE_NAME = "pack.yml";

    private final JavaPlugin plugin;
    private final Supplier<AppConfig> configSupplier;
    private volatile Map<String, ItemPackDefinition> packs = Map.of();

    public ItemPackLoader(JavaPlugin plugin, Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
    }

    public int load() {
        return load(() -> true);
    }

    public int load(BooleanSupplier allowed) {
        if (!allowed(allowed)) {
            return packs.size();
        }
        File root = plugin.getDataFolder().toPath().resolve("items").toFile();
        Map<String, ItemPackDefinition> loaded = new LinkedHashMap<>();
        for (File directory : packDirectories(root)) {
            File file = new File(directory, PACK_FILE_NAME);
            if (!file.isFile()) {
                continue;
            }
            String packId = relativize(directory, root);
            try {
                ItemPackDefinition definition = parse(packId, YamlFiles.load(file));
                if (definition != null) {
                    loaded.put(packId, definition);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not load EmakiItem pack metadata " + file.getPath()
                        + ": " + Texts.toStringSafe(exception.getMessage()));
            }
        }
        if (!allowed(allowed)) {
            return packs.size();
        }
        packs = loaded.isEmpty() ? Map.of() : Map.copyOf(loaded);
        return packs.size();
    }

    public ItemPackDefinition get(String packId) {
        return packs.get(packId == null ? "" : packId);
    }

    public ItemPackDefinition getOrFallback(String packId) {
        ItemPackDefinition definition = get(packId);
        return definition == null ? ItemPackDefinition.fallback(packId == null ? "" : packId) : definition;
    }

    public Map<String, ItemPackDefinition> all() {
        return packs;
    }

    private ItemPackDefinition parse(String packId, YamlSection yaml) {
        if (yaml == null) {
            return ItemPackDefinition.fallback(packId);
        }
        Integer order = yaml.getInt("order", ItemPackDefinition.DEFAULT_ORDER);
        return new ItemPackDefinition(
                packId,
                Texts.toStringSafe(yaml.getString("display_name", "")),
                Texts.toStringSafe(yaml.getString("icon", ItemPackDefinition.DEFAULT_ICON)),
                yaml.getStringList("lore"),
                order == null ? ItemPackDefinition.DEFAULT_ORDER : order
        );
    }

    private List<File> packDirectories(File root) {
        List<File> result = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return result;
        }
        collect(root, 1, maxDepth(), result);
        result.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        return result;
    }

    private void collect(File directory, int depth, int maxDepth, List<File> sink) {
        if (depth >= maxDepth) {
            return;
        }
        File[] entries = directory.listFiles(File::isDirectory);
        if (entries == null) {
            return;
        }
        Arrays.sort(entries, (left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        for (File entry : entries) {
            sink.add(entry);
            collect(entry, depth + 1, maxDepth, sink);
        }
    }

    private String relativize(File directory, File root) {
        String path = directory.getPath();
        String prefix = root.getPath();
        if (!path.startsWith(prefix)) {
            return directory.getName();
        }
        String relative = path.substring(prefix.length()).replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative;
    }

    private boolean allowed(BooleanSupplier allowed) {
        return allowed == null || allowed.getAsBoolean();
    }

    private int maxDepth() {
        AppConfig config = configSupplier == null ? null : configSupplier.get();
        return config == null ? ItemDirectoryConfig.DEFAULT_MAX_DEPTH : config.directories().maxDepth();
    }
}
