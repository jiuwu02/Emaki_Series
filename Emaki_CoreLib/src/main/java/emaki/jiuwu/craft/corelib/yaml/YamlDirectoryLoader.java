package emaki.jiuwu.craft.corelib.yaml;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class YamlDirectoryLoader<T> {

    protected final JavaPlugin plugin;
    protected final Map<String, T> items = new LinkedHashMap<>();
    protected boolean loaded;

    protected YamlDirectoryLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public final int load() {
        items.clear();
        loaded = false;
        File directory = new File(plugin.getDataFolder(), directoryName());
        if (!directory.exists()) {
            try {
                YamlFiles.ensureDirectory(directory.toPath());
            } catch (IOException exception) {
                onDirectoryCreateFailed(directory);
            }
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            loaded = true;
            return 0;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            try {
                T value = parse(file, YamlFiles.load(file));
                if (value == null) {
                    continue;
                }
                String id = idOf(value);
                if (Texts.isBlank(id)) {
                    onBlankId(file);
                    continue;
                }
                if (items.containsKey(id)) {
                    onDuplicateId(file, id);
                    continue;
                }
                items.put(id, value);
            } catch (Exception exception) {
                onLoadFailure(file, exception);
            }
        }
        loaded = true;
        return items.size();
    }

    public final T get(String id) {
        return items.get(id);
    }

    public final Map<String, T> all() {
        return items;
    }

    public final boolean loaded() {
        return loaded;
    }

    protected void onDirectoryCreateFailed(File directory) {
        plugin.getLogger().warning("Failed to create directory: " + directory.getPath());
    }

    protected void onBlankId(File file) {
        plugin.getLogger().warning("Skipped invalid " + typeName() + " config: " + file.getName() + " (blank id)");
    }

    protected void onDuplicateId(File file, String id) {
        plugin.getLogger().warning("Skipped duplicate " + typeName() + " id '" + id + "' from " + file.getName());
    }

    protected void onLoadFailure(File file, Exception exception) {
        plugin.getLogger().warning("Failed to load " + typeName() + " from " + file.getName() + ": " + exception.getMessage());
    }

    protected abstract String directoryName();

    protected abstract String typeName();

    protected abstract T parse(File file, YamlConfiguration configuration);

    protected abstract String idOf(T value);
}
