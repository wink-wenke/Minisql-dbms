package simpledb.storage;

import simpledb.file.FileMgr;
import simpledb.file.Page;
import simpledb.file.BlockId;

/**
 * PageManager 的实现。包装现有 FileMgr + Page。
 */
public class PageManagerImpl implements PageManager {
    private final FileMgr fm;

    public PageManagerImpl(FileMgr fm) {
        this.fm = fm;
    }

    @Override
    public PageId allocatePage() {
        // 追加一个全零页到默认文件
        // 这里使用 append 方式分配新页
        // 具体文件名由调用方通过 writePage 指定
        throw new UnsupportedOperationException(
            "Use allocatePage(String fileName) instead");
    }

    /**
     * 在指定文件中分配一个新页。
     */
    public PageId allocatePage(String fileName) {
        BlockId blk = fm.append(fileName);
        return toPageId(blk);
    }

    @Override
    public void freePage(PageId pageId) {
        // 页释放：将页内容清零写回
        // 完整的空闲页管理需要上层配合
        byte[] zeros = new byte[PAGE_SIZE];
        writePage(pageId, zeros);
    }

    @Override
    public void readPage(PageId pageId, byte[] buffer) {
        BlockId blk = toBlockId(pageId);
        Page p = new Page(buffer);
        fm.read(blk, p);
    }

    @Override
    public void writePage(PageId pageId, byte[] buffer) {
        BlockId blk = toBlockId(pageId);
        Page p = new Page(buffer);
        fm.write(blk, p);
    }

    @Override
    public int pageCount(String fileName) {
        return fm.length(fileName);
    }

    private BlockId toBlockId(PageId pageId) {
        return new BlockId(pageId.fileName(), pageId.pageNumber());
    }

    private PageId toPageId(BlockId blk) {
        return new PageId(blk.fileName(), blk.number());
    }
}
