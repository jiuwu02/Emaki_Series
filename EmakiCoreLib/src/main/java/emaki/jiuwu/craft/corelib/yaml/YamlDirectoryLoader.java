package emaki.jiuwu.craft.corelib.yaml;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.Texts;

public abstract class YamlDirectoryLoader<T> {

    protected final JavaPlugin plugin;
    protected final Object stateLock = new Object();
    protected final Map<String, T> items = new LinkedHashMap<>();
    protected boolean loaded;

    protected YamlDirectoryLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public final int load() {
        synchronized (stateLock) {
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

    public final boolean loaded() {
        synchronized (stateLock) {
            return loaded;
        }
    }

    protected void onDirectoryCreateFailed(File directory) {
        issue("loader.directory_create_failed", Map.of("path", directory.getPath()));
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

    protected abstract String directoryName();

    protected abstract String typeName();

    protected abstract T parse(File file, YamlConfiguration configuration);

    protected abstract String idOf(T value);

    private void issue(String key, Map<String, ?> replacements) {
        LogMessages messages = messages();
        if (messages == null) {
            return;
        }
        messages.warning(key, replacements);
    }

    private LogMessages messages() {
        if (plugin instanceof LogMessagesProvider provider) {
            return provider.messageService();
        }
        return null;
    }

    private AsyncTaskScheduler resolveScheduler() {
        EmakiCoreLibPlugin coreLibPlugin = EmakiCoreLibPlugin.getInstance();
        return coreLibPlugin == null ? null : coreLibPlugin.asyncTaskScheduler();
    }
}
