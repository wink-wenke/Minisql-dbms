package simpledb.storage;

import simpledb.file.FileMgr;
import simpledb.file.Page;
import simpledb.file.BlockId;
import simpledb.file.PageId;

/**
 * PageManager 的薄适配器。所有页管理逻辑（空闲列表、分配、释放）由 FileMgr 实现。
 */
public class PageManagerImpl implements PageManager {
    private final FileMgr fm;

    public PageManagerImpl(FileMgr fm) {
        this.fm = fm;
    }

    @Override
    public PageId allocatePage() {
        return allocatePage("default.tbl");
    }

    public PageId allocatePage(String fileName) {
        return fm.allocatePage(fileName);
    }

    @Override
    public void freePage(PageId pageId) {
        fm.freePage(pageId.fileName(), pageId.pageNumber());
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

    public int freePageCount(String fileName) {
        return fm.freePageCount(fileName);
    }

    private BlockId toBlockId(PageId pageId) {
        return new BlockId(pageId.fileName(), pageId.pageNumber());
    }
}
