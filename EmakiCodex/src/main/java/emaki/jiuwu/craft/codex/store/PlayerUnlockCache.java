package emaki.jiuwu.craft.codex.store;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.async.ConcurrentDataStore;

/**
 * Thread-safe in-memory cache of {@link PlayerUnlockData} with dirty tracking,
 * mirroring the EmakiForge PlayerDataCache pattern.
 */
final class PlayerUnlockCache {

    private final Map<String, CachedUnlockData> entries = new ConcurrentHashMap<>();

    public <R> R read(String uuid, Supplier<PlayerUnlockData> loader, Function<PlayerUnlockData, R> reader) {
        Objects.requireNonNull(reader, "reader");
        return resolveEntry(uuid, loader).store().read(reader);
    }

    public void update(String uuid, Supplier<PlayerUnlockData> loader, Consumer<PlayerUnlockData> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CachedUnlockData entry = resolveEntry(uuid, loader);
        entry.store().write(data -> {
            consumer.accept(data);
            return data;
        });
        entry.markDirty();
    }

    public PlayerUnlockData snapshot(String uuid) {
        CachedUnlockData entry = entries.get(uuid);
        return entry == null ? null : entry.store().read(PlayerUnlockData::copy);
    }

    public Map<String, PlayerUnlockData> snapshotDirtyEntries() {
        Map<String, PlayerUnlockData> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, CachedUnlockData> entry : entries.entrySet()) {
            if (entry.getValue().dirty()) {
                snapshots.put(entry.getKey(), entry.getValue().store().read(PlayerUnlockData::copy));
            }
        }
        return snapshots;
    }

    public void markClean(String uuid) {
        CachedUnlockData entry = entries.get(uuid);
        if (entry != null) {
            entry.markClean();
        }
    }

    public void remove(String uuid) {
        entries.remove(uuid);
    }

    private CachedUnlockData resolveEntry(String uuid, Supplier<PlayerUnlockData> loader) {
        return entries.computeIfAbsent(uuid, key -> new CachedUnlockData(load(uuid, loader)));
    }

    private PlayerUnlockData load(String uuid, Supplier<PlayerUnlockData> loader) {
        PlayerUnlockData loaded = loader == null ? null : loader.get();
        return loaded == null ? new PlayerUnlockData(uuid) : loaded;
    }

    private static final class CachedUnlockData {

        private final ConcurrentDataStore<PlayerUnlockData> store;
        private final AtomicBoolean dirty = new AtomicBoolean();

        private CachedUnlockData(PlayerUnlockData data) {
            this.store = new ConcurrentDataStore<>(data == null ? new PlayerUnlockData("") : data);
        }

        private ConcurrentDataStore<PlayerUnlockData> store() {
            return store;
        }

        private boolean dirty() {
            return dirty.get();
        }

        private void markDirty() {
            dirty.set(true);
        }

        private void markClean() {
            dirty.set(false);
        }
    }
}
