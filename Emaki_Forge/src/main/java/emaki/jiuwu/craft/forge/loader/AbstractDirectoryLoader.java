package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public abstract class AbstractDirectoryLoader<T> {

    protected final EmakiForgePlugin plugin;
    protected final Map<String, T> items = new LinkedHashMap<>();
    protected boolean loaded;

    protected AbstractDirectoryLoader(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    public int load() {
        items.clear();
        loaded = false;
        File directory = plugin.dataPath(directoryName()).toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + directory.getPath());
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            loaded = true;
            return 0;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            try {
                T value = parse(file, YamlConfiguration.loadConfiguration(file));
                if (value == null) {
                    continue;
                }
                String id = idOf(value);
                if (id == null || items.containsKey(id)) {
                    plugin.getLogger().warning("Skipped duplicate or invalid " + typeName() + " config: " + file.getName());
                    continue;
                }
                items.put(id, value);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to load " + typeName() + " from " + file.getName() + ": " + exception.getMessage());
            }
        }
        loaded = true;
        return items.size();
    }

    protected abstract String directoryName();

    protected abstract String typeName();

    protected abstract T parse(File file, YamlConfiguration configuration);

    protected abstract String idOf(T value);

    public T get(String id) {
        return items.get(id);
    }

    public Map<String, T> all() {
        return items;
    }
}
