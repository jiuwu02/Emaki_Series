package emaki.jiuwu.craft.corelib.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class CacheManager<K, V> {

    private final Cache<K, V> cache;

    public CacheManager(int maxSize, long expireAfterAccessMillis) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maxSize))
                .recordStats();
        if (expireAfterAccessMillis > 0) {
            builder.expireAfterAccess(expireAfterAccessMillis, TimeUnit.MILLISECONDS);
        }
        this.cache = builder.build();
    }

    public V get(K key) {
        return cache.getIfPresent(key);
    }

    public V getOrLoad(K key, Supplier<V> loader) {
        if (loader == null) {
            return cache.getIfPresent(key);
        }
        return cache.get(key, k -> loader.get());
    }

    public void put(K key, V value) {
        if (value == null) {
            cache.invalidate(key);
            return;
        }
        cache.put(key, value);
    }

    public boolean containsKey(K key) {
        return cache.getIfPresent(key) != null;
    }

    public V invalidate(K key) {
        V old = cache.getIfPresent(key);
        cache.invalidate(key);
        return old;
    }

    public void clear() {
        cache.invalidateAll();
    }

    public int size() {
        cache.cleanUp();
        return (int) cache.estimatedSize();
    }

    public Map<K, V> snapshot() {
        cache.cleanUp();
        return Map.copyOf(cache.asMap());
    }

    public CacheStats stats() {
        cache.cleanUp();
        com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = cache.stats();
        return new CacheStats(
                (int) cache.estimatedSize(),
                caffeineStats.hitCount(),
                caffeineStats.missCount(),
                caffeineStats.evictionCount(),
                caffeineStats.evictionCount(),
                caffeineStats.loadCount()
        );
    }
}
