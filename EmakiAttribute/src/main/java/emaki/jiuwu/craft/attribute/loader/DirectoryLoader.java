package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class DirectoryLoader<T> {

    protected final EmakiAttributePlugin plugin;
    protected final Object stateLock = new Object();
    protected final Map<String, T> items = new LinkedHashMap<>();
    protected final List<String> issues = new ArrayList<>();
    protected boolean loaded;

    protected DirectoryLoader(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    public int load() {
        return load(progress -> {
        });
    }

    public int load(Consumer<LoadProgress> progressCallback) {
        synchronized (stateLock) {
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
            if (plugin.configModel().releaseDefaultData()) {
                seedBundledResources(directory);
            }
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
            int total = files == null ? 0 : files.length;
            notifyProgress(progressCallback, 0, total, "", total == 0);
            if (files == null || files.length == 0) {
                loaded = true;
                afterLoad();
                return 0;
            }
            Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
            int processed = 0;
            for (File file : files) {
                try {
                    YamlSection configuration = YamlFiles.load(file);
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
                } finally {
                    processed++;
                    notifyProgress(progressCallback, processed, total, file.getName(), processed >= total);
                }
            }
            afterLoad();
            loaded = true;
            return items.size();
        }
    }

    public CompletableFuture<Integer> loadAsync() {
        return loadAsync(progress -> {
        });
    }

    public CompletableFuture<Integer> loadAsync(Consumer<LoadProgress> progressCallback) {
        AsyncTaskScheduler scheduler = resolveAsyncScheduler();
        if (scheduler == null) {
            return CompletableFuture.completedFuture(load(progressCallback));
        }
        return scheduler.supplyAsync("attribute-loader-" + directoryName(), () -> load(progressCallback));
    }

    public Map<String, T> all() {
        synchronized (stateLock) {
            return Map.copyOf(items);
        }
    }

    public List<String> issues() {
        synchronized (stateLock) {
            return List.copyOf(issues);
        }
    }

    public boolean loaded() {
        synchronized (stateLock) {
            return loaded;
        }
    }

    public T get(String id) {
        synchronized (stateLock) {
            if (Texts.isBlank(id)) {
                return null;
            }
            return items.get(normalizeId(id));
        }
    }

    protected void issue(String key, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        issues.add(localized(key, safeReplacements));
        if (plugin.messageService() != null) {
            plugin.messageService().warning(key, safeReplacements);
        }
    }

    protected String localized(String key, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        if (plugin.messageService() != null) {
            String rendered = plugin.messageService().message(key, safeReplacements);
            if (!Texts.isBlank(rendered) && !key.equals(rendered.trim())) {
                return rendered;
            }
        }
        String safeKey = Texts.toStringSafe(key);
        int separator = safeKey.lastIndexOf('.');
        String token = (separator < 0 ? safeKey : safeKey.substring(separator + 1)).replace('_', ' ').trim();
        String label = Texts.isBlank(token)
                ? "Configuration loader issue"
                : Character.toUpperCase(token.charAt(0)) + token.substring(1);
        if (safeReplacements.isEmpty()) {
            return label;
        }
        StringBuilder builder = new StringBuilder(label).append(": ");
        boolean first = true;
        for (Map.Entry<String, ?> entry : safeReplacements.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(Texts.toStringSafe(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    protected String normalizeId(String id) {
        return Texts.toStringSafe(id).trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    protected void afterLoad() {
    }

    protected void seedBundledResources(File directory) {
    }

    protected boolean validateSchema(File file, YamlSection configuration) {
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

    protected abstract T parse(File file, YamlSection configuration);

    protected abstract String idOf(T value);

    private AsyncTaskScheduler resolveAsyncScheduler() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
    }

    private void notifyProgress(Consumer<LoadProgress> progressCallback,
            int processed,
            int total,
            String currentFile,
            boolean completed) {
        if (progressCallback == null) {
            return;
        }
        progressCallback.accept(new LoadProgress(directoryName(), processed, total, currentFile, completed));
    }

    public record LoadProgress(String directory, int processed, int total, String currentFile, boolean completed) {

    }
}
