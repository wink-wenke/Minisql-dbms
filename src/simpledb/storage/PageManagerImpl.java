package simpledb.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import simpledb.file.FileMgr;
import simpledb.file.Page;
import simpledb.file.BlockId;

/**
 * PageManager 的实现。包装现有 FileMgr + Page。
 * 维护按文件分组的空闲页列表，支持释放后复用。
 */
public class PageManagerImpl implements PageManager {
    private final FileMgr fm;
    /** 每个文件的空闲页号集合（TreeSet 保证复用最小页号） */
    private final Map<String, TreeSet<Integer>> freePages = new HashMap<>();

    public PageManagerImpl(FileMgr fm) {
        this.fm = fm;
    }

    @Override
    public PageId allocatePage() {
        return allocatePage("default.tbl");
    }

    /**
     * 在指定文件中分配一个新页。
     * 优先从空闲列表复用已释放的页，否则追加新页。
     */
    public PageId allocatePage(String fileName) {
        TreeSet<Integer> free = freePages.get(fileName);
        if (free != null && !free.isEmpty()) {
            int pageNum = free.first();
            free.remove(pageNum);
            // 清零已释放的页内容
            byte[] zeros = new byte[PAGE_SIZE];
            PageId pid = new PageId(fileName, pageNum);
            writePage(pid, zeros);
            return pid;
        }
        BlockId blk = fm.append(fileName);
        return toPageId(blk);
    }

    @Override
    public void freePage(PageId pageId) {
        // 清零页内容
        byte[] zeros = new byte[PAGE_SIZE];
        writePage(pageId, zeros);
        // 加入空闲列表
        freePages.computeIfAbsent(pageId.fileName(), k -> new TreeSet<>())
                 .add(pageId.pageNumber());
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

    /**
     * 查询指定文件当前空闲页数量。
     */
    public int freePageCount(String fileName) {
        TreeSet<Integer> free = freePages.get(fileName);
        return free == null ? 0 : free.size();
    }

    private BlockId toBlockId(PageId pageId) {
        return new BlockId(pageId.fileName(), pageId.pageNumber());
    }

    private PageId toPageId(BlockId blk) {
        return new PageId(blk.fileName(), blk.number());
    }
}
