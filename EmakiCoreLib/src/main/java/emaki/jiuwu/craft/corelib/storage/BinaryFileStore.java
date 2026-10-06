package emaki.jiuwu.craft.corelib.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;

public final class BinaryFileStore implements PersistentStore<Map<String, byte[]>> {

    private final FileScope scope;
    private final Path path;

    public BinaryFileStore(FileScope scope, Path path) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public StoreFormat format() {
        return StoreFormat.BINARY;
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
    public CompletableFuture<Map<String, byte[]>> load() {
        return scope.read(path, taskName("load"), () -> BinaryFileFormat.read(path));
    }

    @Override
    public CompletableFuture<Void> save(Map<String, byte[]> snapshot) {
        Map<String, byte[]> entries = snapshot == null ? new LinkedHashMap<>() : snapshot;
        return scope.write(path, taskName("save"), () -> BinaryFileFormat.write(path, entries));
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
        return "storage-binary-" + action + ":" + path.getFileName();
    }
}
