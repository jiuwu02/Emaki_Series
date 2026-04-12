package emaki.jiuwu.craft.corelib.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public final class CacheManager<K, V> {

    private final int maxSize;
    private final long expireAfterAccessMillis;
    private final LinkedHashMap<K, CacheEntry<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong expirations = new AtomicLong();
    private final AtomicLong loads = new AtomicLong();

    public CacheManager(int maxSize, long expireAfterAccessMillis) {
        this.maxSize = Math.max(1, maxSize);
        this.expireAfterAccessMillis = Math.max(0L, expireAfterAccessMillis);
    }

    public V get(K key) {
        long now = System.currentTimeMillis();
        CacheEntry<V> entry;
        readLock.lock();
        try {
            entry = entries.get(key);
        } finally {
            readLock.unlock();
        }
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        writeLock.lock();
        try {
            CacheEntry<V> current = entries.remove(key);
            if (current == null) {
                misses.incrementAndGet();
                return null;
            }
            if (isExpired(current, now)) {
                expirations.incrementAndGet();
                misses.incrementAndGet();
                return null;
            }
            current.touch(now);
            entries.put(key, current);
            hits.incrementAndGet();
            return current.value();
        } finally {
            writeLock.unlock();
        }
    }

    public V getOrLoad(K key, Supplier<V> loader) {
        writeLock.lock();
        try {
            long now = System.currentTimeMillis();
            CacheEntry<V> existing = entries.remove(key);
            if (existing != null && !isExpired(existing, now)) {
                existing.touch(now);
                entries.put(key, existing);
                hits.incrementAndGet();
                return existing.value();
            }
            if (existing != null) {
                expirations.incrementAndGet();
            }
            misses.incrementAndGet();
            if (loader == null) {
                return null;
            }
            V loaded = loader.get();
            if (loaded == null) {
                return null;
            }
            loads.incrementAndGet();
            entries.put(key, new CacheEntry<>(loaded, now));
            trimToSize();
            return loaded;
        } finally {
            writeLock.unlock();
        }
    }

    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        writeLock.lock();
        try {
            if (value == null) {
                entries.remove(key);
                return;
            }
            long now = System.currentTimeMillis();
            entries.put(key, new CacheEntry<>(value, now));
            trimToSize();
        } finally {
            writeLock.unlock();
        }
    }

    public boolean containsKey(K key) {
        return get(key) != null;
    }

    public V invalidate(K key) {
        writeLock.lock();
        try {
            CacheEntry<V> removed = entries.remove(key);
            return removed == null ? null : removed.value();
        } finally {
            writeLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            entries.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public int size() {
        if (expireAfterAccessMillis > 0L) {
            pruneExpired();
        }
        readLock.lock();
        try {
            return entries.size();
        } finally {
            readLock.unlock();
        }
    }

    public Map<K, V> snapshot() {
        if (expireAfterAccessMillis > 0L) {
            pruneExpired();
        }
        readLock.lock();
        try {
            Map<K, V> snapshot = new LinkedHashMap<>();
            for (Map.Entry<K, CacheEntry<V>> entry : entries.entrySet()) {
                snapshot.put(entry.getKey(), entry.getValue().value());
            }
            return Map.copyOf(snapshot);
        } finally {
            readLock.unlock();
        }
    }

    public CacheStats stats() {
        if (expireAfterAccessMillis > 0L) {
            pruneExpired();
        }
        readLock.lock();
        try {
            return new CacheStats(entries.size(), hits.get(), misses.get(), evictions.get(), expirations.get(), loads.get());
        } finally {
            readLock.unlock();
        }
    }

    private void pruneExpired() {
        writeLock.lock();
        try {
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
        } finally {
            writeLock.unlock();
        }
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
