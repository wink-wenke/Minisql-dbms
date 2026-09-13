package simpledb.storage;

/**
 * 页管理器接口。提供页的分配、释放、读写能力。
 * 页大小固定为 4096 字节 (4KB)。
 */
public interface PageManager {

    /** 页大小（字节） */
    int PAGE_SIZE = 4096;

    /**
     * 分配一个新页，返回其 PageId。
     * 新页内容全零。
     */
    PageId allocatePage();

    /**
     * 释放一个页，将其加入空闲列表。
     */
    void freePage(PageId pageId);

    /**
     * 读取指定页的内容到 buffer。
     */
    void readPage(PageId pageId, byte[] buffer);

    /**
     * 将 buffer 内容写入指定页。
     */
    void writePage(PageId pageId, byte[] buffer);

    /**
     * 获取指定文件的总页数。
     */
    int pageCount(String fileName);
}
