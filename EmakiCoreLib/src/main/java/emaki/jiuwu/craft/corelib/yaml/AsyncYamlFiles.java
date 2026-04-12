package emaki.jiuwu.craft.corelib.yaml;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;

public final class AsyncYamlFiles {

    private final AsyncFileService fileService;

    public AsyncYamlFiles(AsyncFileService fileService) {
        this.fileService = fileService;
    }

    public CompletableFuture<YamlSection> load(File file) {
        if (fileService == null) {
            return CompletableFuture.completedFuture(YamlFiles.load(file));
        }
        return fileService.read("yaml-load:" + safeName(file), () -> YamlFiles.load(file));
    }

    public CompletableFuture<Void> save(File file, YamlSection section) {
        if (fileService == null) {
            try {
                YamlFiles.save(file, section);
                return CompletableFuture.completedFuture(null);
            } catch (IOException exception) {
                return failedFuture(exception);
            }
        }
        return fileService.write(file == null ? null : file.toPath(), "yaml-save:" + safeName(file), () -> {
            try {
                YamlFiles.save(file, section);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public CompletableFuture<Void> save(File file, Map<String, ?> values) {
        if (fileService == null) {
            try {
                YamlFiles.save(file, values);
                return CompletableFuture.completedFuture(null);
            } catch (IOException exception) {
                return failedFuture(exception);
            }
        }
        return fileService.write(file == null ? null : file.toPath(), "yaml-save:" + safeName(file), () -> {
            try {
                YamlFiles.save(file, values);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public CompletableFuture<Void> waitForIdle() {
        return fileService == null ? CompletableFuture.completedFuture(null) : fileService.waitForIdle();
    }

    private String safeName(File file) {
        return file == null ? "unknown" : file.getName();
    }

    private <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
