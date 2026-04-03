package emaki.jiuwu.craft.corelib.cache;

public record CacheStats(int size,
                         long hits,
                         long misses,
                         long evictions,
                         long expirations,
                         long loads) {

    public double hitRate() {
        long totalLookups = hits + misses;
        return totalLookups <= 0L ? 1D : hits / (double) totalLookups;
    }
}
