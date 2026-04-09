package emaki.jiuwu.craft.corelib.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class CacheManager<K, V> {

    private final int maxSize;
    private final long expireAfterAccessMillis;
    private final LinkedHashMap<K, CacheEntry<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong expirations = new AtomicLong();
    private final AtomicLong loads = new AtomicLong();

    public CacheManager(int maxSize, long expireAfterAccessMillis) {
        this.maxSize = Math.max(1, maxSize);
        this.expireAfterAccessMillis = Math.max(0L, expireAfterAccessMillis);
    }

    public synchronized V get(K key) {
        long now = System.currentTimeMillis();
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (isExpired(entry, now)) {
            entries.remove(key);
            expirations.incrementAndGet();
            misses.incrementAndGet();
            return null;
        }
        entry.touch(now);
        hits.incrementAndGet();
        return entry.value();
    }

    public synchronized V getOrLoad(K key, Supplier<V> loader) {
        V cached = get(key);
        if (cached != null || loader == null) {
            return cached;
        }
        V loaded = loader.get();
        if (loaded != null) {
            loads.incrementAndGet();
            put(key, loaded);
        }
        return loaded;
    }

    public synchronized void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            entries.remove(key);
            return;
        }
        long now = System.currentTimeMillis();
        entries.put(key, new CacheEntry<>(value, now));
        trimToSize();
    }

    public synchronized boolean containsKey(K key) {
        return get(key) != null;
    }

    public synchronized V invalidate(K key) {
        CacheEntry<V> removed = entries.remove(key);
        return removed == null ? null : removed.value();
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        pruneExpired();
        return entries.size();
    }

    public synchronized Map<K, V> snapshot() {
        pruneExpired();
        Map<K, V> snapshot = new LinkedHashMap<>();
        for (Map.Entry<K, CacheEntry<V>> entry : entries.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().value());
        }
        return Map.copyOf(snapshot);
    }

    public synchronized CacheStats stats() {
        pruneExpired();
        return new CacheStats(entries.size(), hits.get(), misses.get(), evictions.get(), expirations.get(), loads.get());
    }

    private void pruneExpired() {
        if (expireAfterAccessMillis <= 0L || entries.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> {
            if (!isExpired(entry.getValue(), now)) {
                return false;
            }
            expirations.incrementAndGet();
            return true;
        });
    }

    private void trimToSize() {
        while (entries.size() > maxSize) {
            K eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
            evictions.incrementAndGet();
        }
    }

    private boolean isExpired(CacheEntry<V> entry, long now) {
        return expireAfterAccessMillis > 0L
                && entry != null
                && now - entry.lastAccessedAt() >= expireAfterAccessMillis;
    }

    private static final class CacheEntry<V> {

        private final V value;
        private volatile long lastAccessedAt;

        private CacheEntry(V value, long lastAccessedAt) {
            this.value = value;
            this.lastAccessedAt = lastAccessedAt;
        }

        private V value() {
            return value;
        }

        private long lastAccessedAt() {
            return lastAccessedAt;
        }

        private void touch(long now) {
            this.lastAccessedAt = now;
        }
    }
}
