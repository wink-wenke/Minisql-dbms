package simpledb.storage;

/**
 * 缓冲池替换策略。
 */
public enum ReplacementPolicy {
    LRU,    // 最近最少使用
    FIFO    // 先进先出
}
