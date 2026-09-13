package simpledb.storage;

/**
 * 缓存管理器接口。在内存中缓存磁盘页，减少 I/O。
 * 支持 LRU 和 FIFO 两种替换策略。
 * 提供缓存命中/未命中统计。
 */
public interface BufferManager {

    /**
     * 获取指定页。如果页已在缓存中则直接返回（HIT），
     * 否则从磁盘读入缓存（MISS），必要时淘汰一页。
     *
     * @param pageId 要获取的页
     * @return 页内容的 byte[]
     */
    byte[] getPage(PageId pageId);

    /**
     * 将指定页写回磁盘（如果 dirty）。
     */
    void flushPage(PageId pageId);

    /**
     * 将所有脏页写回磁盘。
     */
    void flushAll();

    /**
     * 标记指定页为 unpinned（可被淘汰）。
     */
    void unpinPage(PageId pageId);

    /**
     * 获取缓存统计信息。
     */
    CacheStats getStats();

    /**
     * 设置替换策略。
     */
    void setReplacementPolicy(ReplacementPolicy policy);
}
