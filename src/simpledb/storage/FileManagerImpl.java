package simpledb.storage;

import simpledb.file.BlockId;
import simpledb.file.FileMgr;
import simpledb.file.Page;

/**
 * FileManager 的实现。内部持有 FileMgr，方法直接委托。
 * PageId 与 BlockId 之间做转换。
 */
public class FileManagerImpl implements FileManager {
    private final FileMgr fm;

    public FileManagerImpl(FileMgr fm) {
        this.fm = fm;
    }

    @Override
    public void read(PageId pageId, byte[] buffer) {
        BlockId blk = toBlockId(pageId);
        Page p = new Page(buffer);
        fm.read(blk, p);
    }

    @Override
    public void write(PageId pageId, byte[] buffer) {
        BlockId blk = toBlockId(pageId);
        Page p = new Page(buffer);
        fm.write(blk, p);
    }

    @Override
    public PageId append(String fileName) {
        BlockId blk = fm.append(fileName);
        return toPageId(blk);
    }

    @Override
    public int length(String fileName) {
        return fm.length(fileName);
    }

    @Override
    public int blockSize() {
        return fm.blockSize();
    }

    private BlockId toBlockId(PageId pageId) {
        return new BlockId(pageId.fileName(), pageId.pageNumber());
    }

    private PageId toPageId(BlockId blk) {
        return new PageId(blk.fileName(), blk.number());
    }
}
