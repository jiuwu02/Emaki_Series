package emaki.jiuwu.craft.corelib.yaml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public abstract class YamlDirectoryLoader<T> {

    protected final JavaPlugin plugin;
    protected final Object stateLock = new Object();
    protected final Map<String, T> items = new LinkedHashMap<>();
    protected final Map<String, LoadedYamlEntry<T>> loadedEntries = new LinkedHashMap<>();
    protected final List<String> issues = new ArrayList<>();
    protected boolean loaded;

    protected YamlDirectoryLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public final int load() {
        synchronized (stateLock) {
            items.clear();
            loadedEntries.clear();
            issues.clear();
            loaded = false;
            File directory = new File(plugin.getDataFolder(), directoryName());
            if (!directory.exists()) {
                try {
                    YamlFiles.ensureDirectory(directory.toPath());
                } catch (IOException exception) {
                    onDirectoryCreateFailed(directory);
                }
            }
            List<File> files = collectYamlFiles(directory);
            if (files.isEmpty()) {
                loaded = true;
                return 0;
            }
            try {
                beforeFilesLoaded(directory, List.copyOf(files));
            } catch (RuntimeException exception) {
                onPreparationFailure(directory, exception);
            }
            for (File file : files) {
                try {
                    YamlSection configuration = YamlFiles.load(file);
                    T value = parse(file, configuration);
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
                    loadedEntries.put(id, new LoadedYamlEntry<>(id, file, cloneConfiguration(configuration), value));
                } catch (Exception exception) {
                    onLoadFailure(file, exception);
                }
            }
            loaded = true;
            return items.size();
        }
    }

    public final CompletableFuture<Integer> loadAsync() {
        AsyncTaskScheduler scheduler = resolveScheduler();
        if (scheduler == null) {
            return CompletableFuture.completedFuture(load());
        }
        return scheduler.supplyAsync("yaml-loader-" + directoryName(), this::load);
    }

    public final T get(String id) {
        synchronized (stateLock) {
            return items.get(id);
        }
    }

    public final Map<String, T> all() {
        synchronized (stateLock) {
            return Map.copyOf(items);
        }
    }

    public final List<String> issues() {
        synchronized (stateLock) {
            return List.copyOf(issues);
        }
    }

    public final LoadedYamlEntry<T> entry(String id) {
        synchronized (stateLock) {
            return Texts.isBlank(id) ? null : loadedEntries.get(id);
        }
    }

    public final Map<String, LoadedYamlEntry<T>> entries() {
        synchronized (stateLock) {
            return Map.copyOf(loadedEntries);
        }
    }

    public final boolean loaded() {
        synchronized (stateLock) {
            return loaded;
        }
    }

    protected void onDirectoryCreateFailed(File directory) {
        issue("loader.directory_create_failed", Map.of("type", typeName(), "path", directory.getPath()));
    }

    protected void onBlankId(File file) {
        issue("loader.invalid_blank_id", Map.of("type", typeName(), "file", file.getName()));
    }

    protected void onDuplicateId(File file, String id) {
        issue("loader.duplicate_id", Map.of("type", typeName(), "file", file.getName(), "id", id));
    }

    protected void onLoadFailure(File file, Exception exception) {
        issue("loader.load_failed", Map.of(
                "type", typeName(),
                "file", file.getName(),
                "error", Texts.toStringSafe(exception.getMessage())
        ));
    }

    protected void beforeFilesLoaded(File directory, List<File> files) {
    }

    protected void onPreparationFailure(File directory, RuntimeException exception) {
        String message = "Failed to prepare " + typeName() + " configuration files in "
                + directory.getPath() + ": " + Texts.toStringSafe(exception.getMessage());
        issues.add(message);
        plugin.getLogger().warning(message);
    }

    protected abstract String directoryName();

    protected abstract String typeName();

    protected abstract T parse(File file, YamlSection configuration);

    protected abstract String idOf(T value);

    protected final String localized(String key) {
        return localized(key, Map.of());
    }

    protected final String localized(String key, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        LogMessages messages = messages();
        if (messages != null) {
            String rendered = messages.message(key, safeReplacements);
            if (!Texts.isBlank(rendered) && !key.equals(rendered.trim())) {
                return rendered;
            }
        }
        return fallbackMessage(key, safeReplacements);
    }

    protected final void issue(String key, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        issues.add(localized(key, safeReplacements));
        LogMessages messages = messages();
        if (messages != null) {
            messages.warning(key, safeReplacements);
        }
    }

    private String fallbackMessage(String key, Map<String, ?> replacements) {
        String safeKey = Texts.toStringSafe(key);
        int separator = safeKey.lastIndexOf('.');
        String token = (separator < 0 ? safeKey : safeKey.substring(separator + 1)).replace('_', ' ').trim();
        String label = Texts.isBlank(token)
                ? "Configuration loader issue"
                : Character.toUpperCase(token.charAt(0)) + token.substring(1);
        if (replacements == null || replacements.isEmpty()) {
            return label;
        }
        StringBuilder builder = new StringBuilder(label).append(": ");
        boolean first = true;
        for (Map.Entry<String, ?> entry : replacements.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(Texts.toStringSafe(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    private LogMessages messages() {
        if (plugin instanceof LogMessagesProvider provider) {
            return provider.messageService();
        }
        return null;
    }

    private AsyncTaskScheduler resolveScheduler() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
    }

    private YamlSection cloneConfiguration(YamlSection configuration) {
        return configuration == null ? new MapYamlSection() : configuration.copy();
    }

    private List<File> collectYamlFiles(File directory) {
        List<File> files = new ArrayList<>();
        collectYamlFiles(directory, files);
        files.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        return files;
    }

    private void collectYamlFiles(File directory, List<File> sink) {
        if (directory == null || sink == null || !directory.exists()) {
            return;
        }
        File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry == null) {
                continue;
            }
            if (entry.isDirectory()) {
                collectYamlFiles(entry, sink);
                continue;
            }
            String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                sink.add(entry);
            }
        }
    }

    public record LoadedYamlEntry<T>(String id, File file, YamlSection configuration, T value) {

    }
}
