package simpledb.storage;

import simpledb.file.PageId;

/**
 * 文件管理器接口。管理数据库目录下的文件读写。
 * 提供底层磁盘 I/O 能力。
 */
public interface FileManager {

    /**
     * 读取指定页的内容到 buffer。
     */
    void read(PageId pageId, byte[] buffer);

    /**
     * 将 buffer 内容写入指定页。
     */
    void write(PageId pageId, byte[] buffer);

    /**
     * 追加一个新页到文件末尾，返回其 PageId。
     */
    PageId append(String fileName);

    /**
     * 获取指定文件的页数。
     */
    int length(String fileName);

    /**
     * 获取页大小（字节）。
     */
    int blockSize();

    /**
     * 在指定文件中分配一个新页（优先复用空闲页）。
     */
    PageId allocatePage(String fileName);

    /**
     * 释放一个页，清零后加入空闲列表。
     */
    void freePage(String fileName, int pageNum);
}
