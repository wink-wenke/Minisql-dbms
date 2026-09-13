package simpledb.storage;

/**
 * 缓存统计信息。记录命中/未命中/淘汰次数。
 */
public class CacheStats {
    private long accessCount;
    private long hitCount;
    private long missCount;
    private long evictionCount;

    public CacheStats() {
        reset();
    }

    public void reset() {
        accessCount = 0;
        hitCount = 0;
        missCount = 0;
        evictionCount = 0;
    }

    public void recordHit() {
        accessCount++;
        hitCount++;
    }

    public void recordMiss() {
        accessCount++;
        missCount++;
    }

    public void recordEviction() {
        evictionCount++;
    }

    public long getAccessCount() { return accessCount; }
    public long getHitCount() { return hitCount; }
    public long getMissCount() { return missCount; }
    public long getEvictionCount() { return evictionCount; }

    public double hitRate() {
        return accessCount == 0 ? 0 : (double) hitCount / accessCount;
    }

    public String toString() {
        return String.format(
            "CacheStats{access=%d, hit=%d, miss=%d, eviction=%d, hitRate=%.2f%%}",
            accessCount, hitCount, missCount, evictionCount, hitRate() * 100
        );
    }
}
