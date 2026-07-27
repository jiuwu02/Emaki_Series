package emaki.jiuwu.craft.corelib.yaml;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;

public final class AsyncYamlFiles {

    private final FileScope fileScope;

    public AsyncYamlFiles(AsyncFileService fileService) {
        this(Objects.requireNonNull(fileService, "fileService").defaultScope());
    }

    public AsyncYamlFiles(FileScope fileScope) {
        this.fileScope = Objects.requireNonNull(fileScope, "fileScope");
    }

    public <T> CompletableFuture<T> read(String taskName, Supplier<T> action) {
        return fileScope.read(taskName, action);
    }

    public CompletableFuture<YamlSection> load(File file) {
        if (file == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("file"));
        }
        return fileScope.read(file.toPath(), "yaml-load:" + safeName(file), () -> YamlFiles.load(file));
    }

    public CompletableFuture<Void> save(File file, YamlSection section) {
        if (file == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("file"));
        }
        return fileScope.write(file.toPath(), "yaml-save:" + safeName(file), () -> {
            try {
                YamlFiles.save(file, section);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public CompletableFuture<Void> save(File file, Map<String, ?> values) {
        if (file == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("file"));
        }
        return fileScope.write(file.toPath(), "yaml-save:" + safeName(file), () -> {
            try {
                YamlFiles.save(file, values);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public CompletableFuture<Void> waitForIdle() {
        return fileScope.waitForIdle();
    }

    public DrainResult sealAndDrain(long timeout, TimeUnit unit) {
        return fileScope.sealAndDrain(timeout, unit);
    }

    public int pendingOperationCount() {
        return fileScope.pendingOperationCount();
    }

    private String safeName(File file) {
        return file == null ? "unknown" : file.getName();
    }
}
