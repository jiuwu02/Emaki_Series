package emaki.jiuwu.craft.forge.loader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.async.ConcurrentDataStore;
import emaki.jiuwu.craft.forge.model.PlayerData;

final class PlayerDataCache {

    private final Map<String, CachedPlayerData> entries = new ConcurrentHashMap<>();

    public PlayerData getOrLoad(String uuid, Supplier<PlayerData> loader) {
        return read(uuid, loader, PlayerData::copy);
    }

    public <R> R read(String uuid, Supplier<PlayerData> loader, Function<PlayerData, R> reader) {
        Objects.requireNonNull(reader, "reader");
        CachedPlayerData entry = resolveEntry(uuid, loader);
        return entry.store().read(reader);
    }

    public void update(String uuid, Supplier<PlayerData> loader, Consumer<PlayerData> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CachedPlayerData entry = resolveEntry(uuid, loader);
        entry.store().write(data -> {
            consumer.accept(data);
            return data;
        });
        entry.markDirty();
    }

    public PlayerData snapshot(String uuid) {
        CachedPlayerData entry = entries.get(uuid);
        if (entry == null) {
            return null;
        }
        return entry.store().read(PlayerData::copy);
    }

    public Map<String, PlayerData> snapshotDirtyEntries() {
        Map<String, PlayerData> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, CachedPlayerData> entry : entries.entrySet()) {
            if (!entry.getValue().dirty()) {
                continue;
            }
            snapshots.put(entry.getKey(), entry.getValue().store().read(PlayerData::copy));
        }
        return snapshots;
    }

    public void markClean(String uuid) {
        CachedPlayerData entry = entries.get(uuid);
        if (entry != null) {
            entry.markClean();
        }
    }

    public boolean isDirty(String uuid) {
        CachedPlayerData entry = entries.get(uuid);
        return entry != null && entry.dirty();
    }

    public void remove(String uuid) {
        entries.remove(uuid);
    }

    public int size() {
        return entries.size();
    }

    public int dirtyCount() {
        int count = 0;
        for (CachedPlayerData entry : entries.values()) {
            if (entry.dirty()) {
                count++;
            }
        }
        return count;
    }

    private CachedPlayerData resolveEntry(String uuid, Supplier<PlayerData> loader) {
        return entries.computeIfAbsent(uuid, key -> new CachedPlayerData(load(uuid, loader)));
    }

    private PlayerData load(String uuid, Supplier<PlayerData> loader) {
        PlayerData loaded = loader == null ? null : loader.get();
        return loaded == null ? new PlayerData(uuid) : loaded;
    }

    private static final class CachedPlayerData {

        private final ConcurrentDataStore<PlayerData> store;
        private final AtomicBoolean dirty = new AtomicBoolean();

        private CachedPlayerData(PlayerData data) {
            this.store = new ConcurrentDataStore<>(data == null ? new PlayerData("") : data);
        }

        private ConcurrentDataStore<PlayerData> store() {
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
