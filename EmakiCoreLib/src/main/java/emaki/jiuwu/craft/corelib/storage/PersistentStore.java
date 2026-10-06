package emaki.jiuwu.craft.corelib.storage;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface PersistentStore<T> extends AutoCloseable {

    StoreFormat format();

    Path path();

    boolean exists();

    CompletableFuture<T> load();

    CompletableFuture<Void> save(T snapshot);

    CompletableFuture<Void> flush();

    CompletableFuture<Void> waitForIdle();

    @Override
    void close();
}
