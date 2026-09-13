package simpledb.storage;

import java.util.Iterator;

/**
 * Write-Ahead Log 管理器接口。
 */
public interface LogManager {

    /**
     * 追加一条日志记录。
     *
     * @param record 日志记录字节数组
     * @return 日志序列号 (LSN)
     */
    long append(byte[] record);

    /**
     * 将日志缓冲区刷到磁盘。
     */
    void flush(long lsn);

    /**
     * 创建日志迭代器（从最新到最旧）。
     */
    Iterator<byte[]> iterator();
}
