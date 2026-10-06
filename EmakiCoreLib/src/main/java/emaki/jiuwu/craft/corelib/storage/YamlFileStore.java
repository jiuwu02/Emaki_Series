package emaki.jiuwu.craft.corelib.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;

public final class YamlFileStore implements PersistentStore<Map<String, Object>> {

    private final FileScope scope;
    private final Path path;

    public YamlFileStore(FileScope scope, Path path) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public StoreFormat format() {
        return StoreFormat.YAML;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public boolean exists() {
        return Files.exists(path);
    }

    @Override
    public CompletableFuture<Map<String, Object>> load() {
        return scope.read(path, taskName("load"), () -> YamlFiles.load(path.toFile()).asMap());
    }

    @Override
    public CompletableFuture<Void> save(Map<String, Object> snapshot) {
        Map<String, Object> document = snapshot == null ? new LinkedHashMap<>() : snapshot;
        return scope.write(path, taskName("save"), () -> {
            try {
                YamlFiles.save(path.toFile(), document);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> flush() {
        return scope.waitForIdle();
    }

    @Override
    public CompletableFuture<Void> waitForIdle() {
        return scope.waitForIdle();
    }

    @Override
    public void close() {
    }

    private String taskName(String action) {
        return "storage-yaml-" + action + ":" + path.getFileName();
    }
}
