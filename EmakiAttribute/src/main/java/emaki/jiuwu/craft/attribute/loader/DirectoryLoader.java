package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public abstract class DirectoryLoader<T> {

    protected final EmakiAttributePlugin plugin;
    protected final Map<String, T> items = new LinkedHashMap<>();
    protected final List<String> issues = new ArrayList<>();
    protected boolean loaded;

    protected DirectoryLoader(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    public int load() {
        items.clear();
        issues.clear();
        loaded = false;
        File directory = plugin.dataPath(directoryName()).toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            issue(
                    "loader.directory_create_failed",
                    Map.of(
                            "type", typeName(),
                            "path", directory.getPath()
                    )
            );
        }
        seedBundledResources(directory);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            loaded = true;
            afterLoad();
            return 0;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            try {
                YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
                if (!validateSchema(file, configuration)) {
                    continue;
                }
                T value = parse(file, configuration);
                if (value == null) {
                    continue;
                }
                String id = idOf(value);
                if (Texts.isBlank(id)) {
                    issue(
                            "loader.invalid_blank_id",
                            Map.of(
                                    "type", typeName(),
                                    "file", file.getName()
                            )
                    );
                    continue;
                }
                String normalized = normalizeId(id);
                if (items.containsKey(normalized)) {
                    issue(
                            "loader.duplicate_id",
                            Map.of(
                                    "type", typeName(),
                                    "file", file.getName(),
                                    "id", id
                            )
                    );
                    continue;
                }
                items.put(normalized, value);
            } catch (Exception exception) {
                issue(
                        "loader.load_failed",
                        Map.of(
                                "type", typeName(),
                                "file", file.getName(),
                                "error", Texts.toStringSafe(exception.getMessage())
                        )
                );
            }
        }
        afterLoad();
        loaded = true;
        return items.size();
    }

    public Map<String, T> all() {
        return Map.copyOf(items);
    }

    public List<String> issues() {
        return List.copyOf(issues);
    }

    public boolean loaded() {
        return loaded;
    }

    public T get(String id) {
        if (Texts.isBlank(id)) {
            return null;
        }
        return items.get(normalizeId(id));
    }

    protected void issue(String key, Map<String, ?> replacements) {
        String message = plugin.messageService() == null
                ? key
                : plugin.messageService().message(key, replacements);
        issues.add(message);
        if (plugin.messageService() == null) {
            return;
        }
        plugin.messageService().warning(key, replacements);
    }

    protected String normalizeId(String id) {
        return Texts.toStringSafe(id).trim().toLowerCase().replace(' ', '_');
    }

    protected void afterLoad() {
    }

    protected void seedBundledResources(File directory) {
    }

    protected boolean validateSchema(File file, YamlConfiguration configuration) {
        return true;
    }

    protected void copyBundledResource(String resourcePath, File target) {
        if (target == null || resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        try {
            boolean copied = YamlFiles.copyResourceIfMissing(plugin, resourcePath, target);
            if (!copied && !target.exists()) {
                issue(
                        "loader.bundled_resource_missing",
                        Map.of(
                                "type", typeName(),
                                "path", target.getPath(),
                                "resource", resourcePath
                        )
                );
            }
        } catch (IOException exception) {
            issue(
                    "loader.bundled_resource_write_failed",
                    Map.of(
                            "type", typeName(),
                            "path", target.getPath(),
                            "error", Texts.toStringSafe(exception.getMessage())
                    )
            );
        }
    }

    protected abstract String directoryName();

    protected abstract String typeName();

    protected abstract T parse(File file, YamlConfiguration configuration);

    protected abstract String idOf(T value);
}
